package neqsim.process.equipment.network;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.ProcessEquipmentInterface;

/**
 * Typed field and SURF identity view over a {@link LoopedPipeNetwork}.
 *
 * <p>
 * This class deliberately does not own another hydraulic graph. Every field node and edge identifier is the identifier
 * of the corresponding node or edge in the wrapped {@code LoopedPipeNetwork}. The view adds stable equipment tags,
 * field roles, services, named ports and execution diagnostics while the wrapped network remains the sole owner of
 * connectivity, fluids, pressure-flow equations, conservative junction mixing and optimization state.
 * </p>
 *
 * <p>
 * Equipment bindings are optional runtime references to normal NeqSim process equipment. They are not serialized;
 * stable identifiers, tags and ports are serialized so a replayed definition can be rebound to equipment in a
 * {@code ProcessSystem} or {@code ProcessModel} without duplicating those objects.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class FieldNetworkTopology implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Current JSON definition schema version. */
  private static final int SCHEMA_VERSION = 1;

  /** Field service carried by a node or edge. */
  public enum Service {
    /** Production fluids moving from wells toward a host. */
    PRODUCTION,
    /** Injection fluids moving from a source toward injection wells. */
    INJECTION,
    /** A shared or reversible facility that may participate in either service. */
    SHARED
  }

  /** Semantic role of a field-network node. */
  public enum NodeRole {
    /** Producing well or its network boundary. */
    PRODUCTION_WELL,
    /** Injection well or its network boundary. */
    INJECTION_WELL,
    /** Pump, compressor or host-side source for an injection system. */
    INJECTION_SOURCE,
    /** Subsea tree or wellhead connection. */
    TREE,
    /** Multi-slot subsea template. */
    TEMPLATE,
    /** Production or injection manifold. */
    MANIFOLD,
    /** Pipeline end manifold. */
    PLEM,
    /** Pipeline end termination. */
    PLET,
    /** Hydraulic junction without a separate physical equipment object. */
    JUNCTION,
    /** Host, platform, FPSO or onshore receiving/injection boundary. */
    HOST,
    /** Tie-in to an existing brownfield network. */
    BROWNFIELD_TIE_IN
  }

  /** Semantic role of a field-network edge. */
  public enum EdgeRole {
    /** Reservoir-to-well inflow relationship. */
    WELL_INFLOW,
    /** Well tubing or completion conduit. */
    TUBING,
    /** Production or injection choke. */
    CHOKE,
    /** Short subsea jumper. */
    JUMPER,
    /** Well or template flowline. */
    FLOWLINE,
    /** Common gathering or injection trunkline. */
    TRUNKLINE,
    /** Export or injection pipeline. */
    PIPELINE,
    /** Riser between seabed and host. */
    RISER,
    /** Manifold or host header connection. */
    HEADER,
    /** Pump or liquid booster. */
    PUMP,
    /** Compressor or gas booster. */
    COMPRESSOR,
    /** Brownfield or future tie-in spool. */
    TIE_IN
  }

  /** Nominal edge-flow direction contract. */
  public enum FlowDirection {
    /** Normal operation is from the declared source node to the declared target node. */
    FROM_TO,
    /** Reverse operation is part of the declared operating envelope. */
    BIDIRECTIONAL
  }

  /** Severity of a topology validation issue. */
  public enum Severity {
    /** Execution must not proceed until the issue is corrected. */
    ERROR,
    /** The definition can execute but requires engineering review. */
    WARNING
  }

  /** Immutable typed identity for a node in the wrapped hydraulic graph. */
  public static final class FieldNode implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;

    private final String id;
    private final String equipmentTag;
    private final NodeRole role;
    private final Service service;

    /**
     * Create a field-node identity.
     *
     * @param id canonical hydraulic node identifier
     * @param equipmentTag stable engineering tag
     * @param role field role
     * @param service field service
     */
    private FieldNode(String id, String equipmentTag, NodeRole role, Service service) {
      this.id = id;
      this.equipmentTag = equipmentTag;
      this.role = role;
      this.service = service;
    }

    /**
     * Get the canonical identifier.
     *
     * @return node identifier and wrapped-network node name
     */
    public String getId() {
      return id;
    }

    /**
     * Get the stable engineering tag.
     *
     * @return equipment tag
     */
    public String getEquipmentTag() {
      return equipmentTag;
    }

    /**
     * Get the node role.
     *
     * @return node role
     */
    public NodeRole getRole() {
      return role;
    }

    /**
     * Get the node service.
     *
     * @return node service
     */
    public Service getService() {
      return service;
    }
  }

  /** Immutable typed identity for an edge in the wrapped hydraulic graph. */
  public static final class FieldEdge implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;

    private final String id;
    private final String equipmentTag;
    private final EdgeRole role;
    private final Service service;
    private final FlowDirection flowDirection;
    private final String fromPort;
    private final String toPort;

    /**
     * Create a field-edge identity.
     *
     * @param id canonical hydraulic edge identifier
     * @param equipmentTag stable engineering tag
     * @param role field role
     * @param service field service
     * @param flowDirection nominal direction contract
     * @param fromPort named port on the source node
     * @param toPort named port on the target node
     */
    private FieldEdge(String id, String equipmentTag, EdgeRole role, Service service, FlowDirection flowDirection,
        String fromPort, String toPort) {
      this.id = id;
      this.equipmentTag = equipmentTag;
      this.role = role;
      this.service = service;
      this.flowDirection = flowDirection;
      this.fromPort = fromPort;
      this.toPort = toPort;
    }

    /**
     * Get the canonical identifier.
     *
     * @return edge identifier and wrapped-network pipe name
     */
    public String getId() {
      return id;
    }

    /**
     * Get the stable engineering tag.
     *
     * @return equipment tag
     */
    public String getEquipmentTag() {
      return equipmentTag;
    }

    /**
     * Get the edge role.
     *
     * @return edge role
     */
    public EdgeRole getRole() {
      return role;
    }

    /**
     * Get the edge service.
     *
     * @return edge service
     */
    public Service getService() {
      return service;
    }

    /**
     * Get the nominal flow-direction contract.
     *
     * @return direction contract
     */
    public FlowDirection getFlowDirection() {
      return flowDirection;
    }

    /**
     * Get the source-node port.
     *
     * @return source port
     */
    public String getFromPort() {
      return fromPort;
    }

    /**
     * Get the target-node port.
     *
     * @return target port
     */
    public String getToPort() {
      return toPort;
    }
  }

  /** Structured diagnostic returned by {@link #validate()}. */
  public static final class ValidationIssue implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;

    private final Severity severity;
    private final String code;
    private final String identity;
    private final String message;

    /**
     * Create a validation issue.
     *
     * @param severity issue severity
     * @param code stable diagnostic code
     * @param identity affected node or edge identifier
     * @param message remediation-oriented message
     */
    private ValidationIssue(Severity severity, String code, String identity, String message) {
      this.severity = severity;
      this.code = code;
      this.identity = identity;
      this.message = message;
    }

    /**
     * Get issue severity.
     *
     * @return severity
     */
    public Severity getSeverity() {
      return severity;
    }

    /**
     * Get the stable diagnostic code.
     *
     * @return diagnostic code
     */
    public String getCode() {
      return code;
    }

    /**
     * Get the affected identity.
     *
     * @return node or edge identity, or an empty string for a network issue
     */
    public String getIdentity() {
      return identity;
    }

    /**
     * Get the diagnostic message.
     *
     * @return message
     */
    public String getMessage() {
      return message;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
      return severity + " [" + code + "]" + (identity.isEmpty() ? "" : " " + identity) + ": " + message;
    }
  }

  private final LoopedPipeNetwork hydraulicNetwork;
  private final Map<String, FieldNode> nodes = new LinkedHashMap<String, FieldNode>();
  private final Map<String, FieldEdge> edges = new LinkedHashMap<String, FieldEdge>();
  private transient Map<String, ProcessEquipmentInterface> equipmentBindings = new LinkedHashMap<String, ProcessEquipmentInterface>();

  /**
   * Create a typed topology and its canonical hydraulic network.
   *
   * @param name network name
   */
  public FieldNetworkTopology(String name) {
    this(new LoopedPipeNetwork(requireText(name, "name")));
  }

  /**
   * Create a typed view over an existing hydraulic network.
   *
   * <p>
   * Existing hydraulic nodes and edges must be registered explicitly. This makes missing semantic identity visible in
   * {@link #validate()} instead of guessing engineering roles from names.
   * </p>
   *
   * @param hydraulicNetwork existing canonical hydraulic graph
   */
  public FieldNetworkTopology(LoopedPipeNetwork hydraulicNetwork) {
    this.hydraulicNetwork = Objects.requireNonNull(hydraulicNetwork, "hydraulicNetwork cannot be null");
  }

  /**
   * Get the sole hydraulic and connectivity graph.
   *
   * @return wrapped network
   */
  public LoopedPipeNetwork getHydraulicNetwork() {
    return hydraulicNetwork;
  }

  /**
   * Add a fixed-pressure source boundary.
   *
   * @param id stable node identifier
   * @param equipmentTag stable engineering tag
   * @param role semantic role
   * @param service production, injection or shared service
   * @param pressureBar absolute pressure in bara
   * @param nominalRateKgHr nominal supplied mass rate in kg/hr
   * @param elevationM elevation in metres
   * @return created field identity
   */
  public FieldNode addPressureSource(String id, String equipmentTag, NodeRole role, Service service, double pressureBar,
      double nominalRateKgHr, double elevationM) {
    checkNewIdentity(id);
    hydraulicNetwork.addSourceNode(id, pressureBar, nominalRateKgHr, elevationM);
    return putNode(id, equipmentTag, role, service);
  }

  /**
   * Add a specified-demand sink boundary.
   *
   * @param id stable node identifier
   * @param equipmentTag stable engineering tag
   * @param role semantic role
   * @param service production, injection or shared service
   * @param demandKgHr demanded mass rate in kg/hr
   * @param elevationM elevation in metres
   * @return created field identity
   */
  public FieldNode addDemandSink(String id, String equipmentTag, NodeRole role, Service service, double demandKgHr,
      double elevationM) {
    checkNewIdentity(id);
    hydraulicNetwork.addSinkNode(id, demandKgHr, elevationM);
    return putNode(id, equipmentTag, role, service);
  }

  /**
   * Add a fixed-pressure sink boundary.
   *
   * @param id stable node identifier
   * @param equipmentTag stable engineering tag
   * @param role semantic role
   * @param service production, injection or shared service
   * @param pressureBar absolute pressure in bara
   * @param elevationM elevation in metres
   * @return created field identity
   */
  public FieldNode addFixedPressureSink(String id, String equipmentTag, NodeRole role, Service service,
      double pressureBar, double elevationM) {
    checkNewIdentity(id);
    hydraulicNetwork.addFixedPressureSinkNode(id, pressureBar, elevationM);
    return putNode(id, equipmentTag, role, service);
  }

  /**
   * Add a zero-volume conservative junction.
   *
   * @param id stable node identifier
   * @param equipmentTag stable engineering tag
   * @param role semantic role
   * @param service production, injection or shared service
   * @param elevationM elevation in metres
   * @return created field identity
   */
  public FieldNode addJunction(String id, String equipmentTag, NodeRole role, Service service, double elevationM) {
    checkNewIdentity(id);
    hydraulicNetwork.addJunctionNode(id, elevationM);
    return putNode(id, equipmentTag, role, service);
  }

  /**
   * Add a live well boundary whose pressure and rate are solved by {@link FieldWellNetworkCoupler}.
   *
   * <p>
   * The underlying hydraulic node is a normal free-pressure source for production or sink for injection. The coupler
   * writes a conservative production supply or injection demand at this node and iterates the existing live well
   * equipment against the pressure solved by {@link LoopedPipeNetwork}. No additional graph or hydraulic element is
   * created.
   * </p>
   *
   * @param id stable field and hydraulic node identity
   * @param equipmentTag engineering equipment tag
   * @param role {@link NodeRole#PRODUCTION_WELL} or {@link NodeRole#INJECTION_WELL}
   * @param service matching production or injection service
   * @param initialPressureBar positive initial pressure in bara
   * @param elevationM node elevation in metres
   * @return typed field node
   */
  public FieldNode addLiveWellNode(String id, String equipmentTag, NodeRole role, Service service,
      double initialPressureBar, double elevationM) {
    if (role != NodeRole.PRODUCTION_WELL && role != NodeRole.INJECTION_WELL) {
      throw new IllegalArgumentException("Live well node role must be PRODUCTION_WELL or INJECTION_WELL");
    }
    if ((role == NodeRole.PRODUCTION_WELL && service != Service.PRODUCTION)
        || (role == NodeRole.INJECTION_WELL && service != Service.INJECTION)) {
      throw new IllegalArgumentException("Live well node role and service must match");
    }
    if (!(initialPressureBar > 0.0)) {
      throw new IllegalArgumentException("Live well initial pressure must be positive in bara");
    }
    checkNewIdentity(id);
    if (role == NodeRole.PRODUCTION_WELL) {
      hydraulicNetwork.addSourceNode(id, initialPressureBar, 0.0, elevationM);
      hydraulicNetwork.getNode(id).setPressureFixed(false);
    } else {
      hydraulicNetwork.addSinkNode(id, 0.0, elevationM);
      hydraulicNetwork.setNodePressure(id, initialPressureBar);
    }
    return putNode(id, equipmentTag, role, service);
  }

  /**
   * Register typed identity for a node that already exists in the wrapped graph.
   *
   * @param id existing hydraulic node name
   * @param equipmentTag stable engineering tag
   * @param role semantic role
   * @param service production, injection or shared service
   * @return registered field identity
   */
  public FieldNode registerExistingNode(String id, String equipmentTag, NodeRole role, Service service) {
    checkNewSemanticIdentity(id);
    if (!hydraulicNetwork.getNodeNames().contains(id)) {
      throw new IllegalArgumentException("Hydraulic node '" + id + "' not found");
    }
    return putNode(id, equipmentTag, role, service);
  }

  /**
   * Add a typed pipe-like edge. Hydraulic fidelity remains an explicit property of this exact edge.
   *
   * @param id stable edge identifier
   * @param equipmentTag stable engineering tag
   * @param role semantic role
   * @param service production, injection or shared service
   * @param flowDirection declared direction contract
   * @param fromNode existing source node identifier
   * @param fromPort named source-node port
   * @param toNode existing target node identifier
   * @param toPort named target-node port
   * @param lengthM length in metres
   * @param diameterM inner diameter in metres
   * @param hydraulicModel explicit edge-local hydraulic model
   * @return created hydraulic edge
   */
  public LoopedPipeNetwork.NetworkPipe addPipe(String id, String equipmentTag, EdgeRole role, Service service,
      FlowDirection flowDirection, String fromNode, String fromPort, String toNode, String toPort, double lengthM,
      double diameterM, LoopedPipeNetwork.PipeModelType hydraulicModel) {
    validateEdgeMetadata(equipmentTag, role, service, flowDirection, fromPort, toPort);
    checkNewIdentity(id);
    requireRegisteredNode(fromNode);
    requireRegisteredNode(toNode);
    LoopedPipeNetwork.NetworkPipe pipe = hydraulicNetwork.addPipe(fromNode, toNode, id, lengthM, diameterM);
    pipe.setHydraulicModelType(Objects.requireNonNull(hydraulicModel, "hydraulicModel cannot be null"));
    putEdge(id, equipmentTag, role, service, flowDirection, fromPort, toPort);
    return pipe;
  }

  /**
   * Add a typed choke using the canonical network choke equations.
   *
   * @param id stable edge identifier
   * @param equipmentTag stable engineering tag
   * @param service production, injection or shared service
   * @param flowDirection declared direction contract
   * @param fromNode existing upstream node identifier
   * @param fromPort named upstream-node port
   * @param toNode existing downstream node identifier
   * @param toPort named downstream-node port
   * @param kv valve coefficient in m3/h per square root bar
   * @param openingPercent valve opening in percent
   * @return created canonical choke element
   */
  public LoopedPipeNetwork.NetworkPipe addChoke(String id, String equipmentTag, Service service,
      FlowDirection flowDirection, String fromNode, String fromPort, String toNode, String toPort, double kv,
      double openingPercent) {
    if (!(kv > 0.0) || !Double.isFinite(kv)) {
      throw new IllegalArgumentException("Choke Kv must be finite and positive");
    }
    if (!Double.isFinite(openingPercent) || openingPercent < 0.0 || openingPercent > 100.0) {
      throw new IllegalArgumentException("Choke opening must be finite and in [0, 100] percent");
    }
    validateEdgeMetadata(equipmentTag, EdgeRole.CHOKE, service, flowDirection, fromPort, toPort);
    checkNewIdentity(id);
    requireRegisteredNode(fromNode);
    requireRegisteredNode(toNode);
    LoopedPipeNetwork.NetworkPipe choke = hydraulicNetwork.addChoke(fromNode, toNode, id, kv, openingPercent);
    putEdge(id, equipmentTag, EdgeRole.CHOKE, service, flowDirection, fromPort, toPort);
    return choke;
  }

  /**
   * Add a typed fixed-outlet-pressure liquid pump using the canonical network pump equations.
   *
   * @param id stable edge identifier
   * @param equipmentTag stable engineering tag
   * @param service injection or shared service
   * @param flowDirection declared direction contract
   * @param fromNode existing suction node identifier
   * @param fromPort named suction-node port
   * @param toNode existing discharge node identifier
   * @param toPort named discharge-node port
   * @param outletPressureBara absolute discharge pressure in bara
   * @param efficiency pump efficiency as a fraction in (0, 1]
   * @return created canonical pump element
   */
  public LoopedPipeNetwork.NetworkPipe addPump(String id, String equipmentTag, Service service,
      FlowDirection flowDirection, String fromNode, String fromPort, String toNode, String toPort,
      double outletPressureBara, double efficiency) {
    if (!(outletPressureBara > 0.0) || !Double.isFinite(outletPressureBara)) {
      throw new IllegalArgumentException("Pump outlet pressure must be finite and positive in bara");
    }
    validateEfficiency(efficiency, "Pump");
    validateEdgeMetadata(equipmentTag, EdgeRole.PUMP, service, flowDirection, fromPort, toPort);
    checkNewIdentity(id);
    requireRegisteredNode(fromNode);
    requireRegisteredNode(toNode);
    LoopedPipeNetwork.NetworkPipe pump = hydraulicNetwork.addPump(fromNode, toNode, id, outletPressureBara, efficiency);
    putEdge(id, equipmentTag, EdgeRole.PUMP, service, flowDirection, fromPort, toPort);
    return pump;
  }

  /**
   * Add a typed fixed-differential-pressure liquid pump using the canonical network pump equations.
   *
   * @param id stable edge identifier
   * @param equipmentTag stable engineering tag
   * @param service injection or shared service
   * @param flowDirection declared direction contract
   * @param fromNode existing suction node identifier
   * @param fromPort named suction-node port
   * @param toNode existing discharge node identifier
   * @param toPort named discharge-node port
   * @param differentialPressureBar pressure rise in bar
   * @param efficiency pump efficiency as a fraction in (0, 1]
   * @return created canonical pump element
   */
  public LoopedPipeNetwork.NetworkPipe addPumpDifferentialPressure(String id, String equipmentTag, Service service,
      FlowDirection flowDirection, String fromNode, String fromPort, String toNode, String toPort,
      double differentialPressureBar, double efficiency) {
    if (!(differentialPressureBar > 0.0) || !Double.isFinite(differentialPressureBar)) {
      throw new IllegalArgumentException("Pump differential pressure must be finite and positive in bar");
    }
    validateEfficiency(efficiency, "Pump");
    validateEdgeMetadata(equipmentTag, EdgeRole.PUMP, service, flowDirection, fromPort, toPort);
    checkNewIdentity(id);
    requireRegisteredNode(fromNode);
    requireRegisteredNode(toNode);
    LoopedPipeNetwork.NetworkPipe pump = hydraulicNetwork.addPumpDifferentialPressure(fromNode, toNode, id,
        differentialPressureBar, efficiency);
    putEdge(id, equipmentTag, EdgeRole.PUMP, service, flowDirection, fromPort, toPort);
    return pump;
  }

  /**
   * Add a typed gas compressor or booster using the canonical network compressor equations.
   *
   * @param id stable edge identifier
   * @param equipmentTag stable engineering tag
   * @param service production, injection or shared service
   * @param flowDirection declared direction contract
   * @param fromNode existing suction node identifier
   * @param fromPort named suction-node port
   * @param toNode existing discharge node identifier
   * @param toPort named discharge-node port
   * @param polytropicEfficiency compressor efficiency as a fraction in (0, 1]
   * @return created canonical compressor element
   */
  public LoopedPipeNetwork.NetworkPipe addCompressor(String id, String equipmentTag, Service service,
      FlowDirection flowDirection, String fromNode, String fromPort, String toNode, String toPort,
      double polytropicEfficiency) {
    validateEfficiency(polytropicEfficiency, "Compressor");
    validateEdgeMetadata(equipmentTag, EdgeRole.COMPRESSOR, service, flowDirection, fromPort, toPort);
    checkNewIdentity(id);
    requireRegisteredNode(fromNode);
    requireRegisteredNode(toNode);
    LoopedPipeNetwork.NetworkPipe compressor = hydraulicNetwork.addCompressor(fromNode, toNode, id,
        polytropicEfficiency);
    putEdge(id, equipmentTag, EdgeRole.COMPRESSOR, service, flowDirection, fromPort, toPort);
    return compressor;
  }

  /**
   * Register typed identity for an edge that already exists in the wrapped graph.
   *
   * @param id existing hydraulic edge name
   * @param equipmentTag stable engineering tag
   * @param role semantic role
   * @param service production, injection or shared service
   * @param flowDirection declared direction contract
   * @param fromPort named source-node port
   * @param toPort named target-node port
   * @return registered field identity
   */
  public FieldEdge registerExistingEdge(String id, String equipmentTag, EdgeRole role, Service service,
      FlowDirection flowDirection, String fromPort, String toPort) {
    checkNewSemanticIdentity(id);
    LoopedPipeNetwork.NetworkPipe pipe = hydraulicNetwork.getPipe(id);
    requireRegisteredNode(pipe.getFromNode());
    requireRegisteredNode(pipe.getToNode());
    return putEdge(id, equipmentTag, role, service, flowDirection, fromPort, toPort);
  }

  /**
   * Bind normal NeqSim process equipment to a stable node or edge identity.
   *
   * @param identity registered node or edge identifier
   * @param equipment equipment instance that owns design, cost or process behavior
   * @return this topology for fluent construction
   */
  public FieldNetworkTopology bindEquipment(String identity, ProcessEquipmentInterface equipment) {
    requireSemanticIdentity(identity);
    equipmentBindings().put(identity, Objects.requireNonNull(equipment, "equipment cannot be null"));
    return this;
  }

  /**
   * Get bound process equipment.
   *
   * @param identity registered node or edge identifier
   * @return bound equipment, or {@code null} when no runtime binding exists
   */
  public ProcessEquipmentInterface getBoundEquipment(String identity) {
    requireSemanticIdentity(identity);
    return equipmentBindings().get(identity);
  }

  /**
   * Get a node identity.
   *
   * @param id node identifier
   * @return node identity
   */
  public FieldNode getNode(String id) {
    FieldNode node = nodes.get(id);
    if (node == null) {
      throw new IllegalArgumentException("Field node '" + id + "' not found");
    }
    return node;
  }

  /**
   * Get an edge identity.
   *
   * @param id edge identifier
   * @return edge identity
   */
  public FieldEdge getEdge(String id) {
    FieldEdge edge = edges.get(id);
    if (edge == null) {
      throw new IllegalArgumentException("Field edge '" + id + "' not found");
    }
    return edge;
  }

  /**
   * Get nodes in deterministic insertion order.
   *
   * @return immutable node list
   */
  public List<FieldNode> getNodes() {
    return Collections.unmodifiableList(new ArrayList<FieldNode>(nodes.values()));
  }

  /**
   * Get edges in deterministic insertion order.
   *
   * @return immutable edge list
   */
  public List<FieldEdge> getEdges() {
    return Collections.unmodifiableList(new ArrayList<FieldEdge>(edges.values()));
  }

  /**
   * Validate semantic identity, ports, connectivity and solver applicability before execution.
   *
   * @return deterministic list of structured issues
   */
  public List<ValidationIssue> validate() {
    List<ValidationIssue> issues = new ArrayList<ValidationIssue>();
    appendHydraulicIssues(issues);
    appendCoverageIssues(issues);
    appendPortAndServiceIssues(issues);
    appendElementCompatibilityIssues(issues);
    appendConnectivityIssues(issues);
    appendSolverIssues(issues);
    return Collections.unmodifiableList(issues);
  }

  /**
   * Validate and throw when execution-blocking errors exist.
   *
   * @throws IllegalStateException if one or more error diagnostics are present
   */
  public void validateForExecution() {
    List<ValidationIssue> issues = validate();
    StringBuilder errors = new StringBuilder();
    for (ValidationIssue issue : issues) {
      if (issue.getSeverity() == Severity.ERROR) {
        if (errors.length() > 0) {
          errors.append("; ");
        }
        errors.append(issue.toString());
      }
    }
    if (errors.length() > 0) {
      throw new IllegalStateException(errors.toString());
    }
  }

  /**
   * Serialize the hydraulic definition and typed identities for deterministic replay.
   *
   * <p>
   * Runtime equipment bindings are intentionally excluded. The wrapped network's own replay limitations still apply,
   * including the need to restore external equipment and fluid references documented by
   * {@link LoopedPipeNetwork#fromJson(String)}.
   * </p>
   *
   * @return pretty-printed JSON definition
   */
  public String toJson() {
    JsonObject root = new JsonObject();
    root.addProperty("schema_version", SCHEMA_VERSION);
    root.add("hydraulic_network", JsonParser.parseString(hydraulicNetwork.toJson()));
    JsonArray nodeArray = new JsonArray();
    for (FieldNode node : nodes.values()) {
      JsonObject item = new JsonObject();
      item.addProperty("id", node.getId());
      item.addProperty("equipment_tag", node.getEquipmentTag());
      item.addProperty("role", node.getRole().name());
      item.addProperty("service", node.getService().name());
      nodeArray.add(item);
    }
    root.add("nodes", nodeArray);
    JsonArray edgeArray = new JsonArray();
    for (FieldEdge edge : edges.values()) {
      JsonObject item = new JsonObject();
      item.addProperty("id", edge.getId());
      item.addProperty("equipment_tag", edge.getEquipmentTag());
      item.addProperty("role", edge.getRole().name());
      item.addProperty("service", edge.getService().name());
      item.addProperty("flow_direction", edge.getFlowDirection().name());
      item.addProperty("from_port", edge.getFromPort());
      item.addProperty("to_port", edge.getToPort());
      edgeArray.add(item);
    }
    root.add("edges", edgeArray);
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(root);
  }

  /**
   * Restore a typed topology definition.
   *
   * @param json definition produced by {@link #toJson()}
   * @return replayed topology with no runtime equipment bindings
   * @throws IllegalArgumentException if the schema or semantic identities are invalid
   */
  public static FieldNetworkTopology fromJson(String json) {
    JsonObject root = JsonParser.parseString(requireText(json, "json")).getAsJsonObject();
    int schemaVersion = root.get("schema_version").getAsInt();
    if (schemaVersion != SCHEMA_VERSION) {
      throw new IllegalArgumentException("Unsupported field network schema version " + schemaVersion);
    }
    LoopedPipeNetwork network = LoopedPipeNetwork.fromJson(root.get("hydraulic_network").toString());
    FieldNetworkTopology topology = new FieldNetworkTopology(network);
    for (JsonElement element : root.getAsJsonArray("nodes")) {
      JsonObject item = element.getAsJsonObject();
      topology.registerExistingNode(item.get("id").getAsString(), item.get("equipment_tag").getAsString(),
          NodeRole.valueOf(item.get("role").getAsString()), Service.valueOf(item.get("service").getAsString()));
    }
    for (JsonElement element : root.getAsJsonArray("edges")) {
      JsonObject item = element.getAsJsonObject();
      topology.registerExistingEdge(item.get("id").getAsString(), item.get("equipment_tag").getAsString(),
          EdgeRole.valueOf(item.get("role").getAsString()), Service.valueOf(item.get("service").getAsString()),
          FlowDirection.valueOf(item.get("flow_direction").getAsString()), item.get("from_port").getAsString(),
          item.get("to_port").getAsString());
    }
    return topology;
  }

  /**
   * Copy the serializable definition without duplicating runtime equipment objects.
   *
   * @return replayed definition copy
   */
  public FieldNetworkTopology copyDefinition() {
    return fromJson(toJson());
  }

  /**
   * Store a node identity after its hydraulic node has been created or verified.
   *
   * @param id node identifier
   * @param equipmentTag equipment tag
   * @param role node role
   * @param service node service
   * @return stored identity
   */
  private FieldNode putNode(String id, String equipmentTag, NodeRole role, Service service) {
    FieldNode node = new FieldNode(requireText(id, "id"), requireText(equipmentTag, "equipmentTag"),
        Objects.requireNonNull(role, "role cannot be null"), Objects.requireNonNull(service, "service cannot be null"));
    nodes.put(id, node);
    return node;
  }

  /**
   * Store an edge identity after its hydraulic edge has been created or verified.
   *
   * @param id edge identifier
   * @param equipmentTag equipment tag
   * @param role edge role
   * @param service edge service
   * @param flowDirection direction contract
   * @param fromPort source port
   * @param toPort target port
   * @return stored identity
   */
  private FieldEdge putEdge(String id, String equipmentTag, EdgeRole role, Service service, FlowDirection flowDirection,
      String fromPort, String toPort) {
    FieldEdge edge = new FieldEdge(requireText(id, "id"), requireText(equipmentTag, "equipmentTag"),
        Objects.requireNonNull(role, "role cannot be null"), Objects.requireNonNull(service, "service cannot be null"),
        Objects.requireNonNull(flowDirection, "flowDirection cannot be null"), requireText(fromPort, "fromPort"),
        requireText(toPort, "toPort"));
    edges.put(id, edge);
    return edge;
  }

  /**
   * Reject an identifier already present in either semantic or hydraulic topology.
   *
   * @param id proposed identifier
   */
  private void checkNewIdentity(String id) {
    checkNewSemanticIdentity(id);
    if (hydraulicNetwork.getNodeNames().contains(id) || hydraulicNetwork.getPipeNames().contains(id)) {
      throw new IllegalArgumentException("Hydraulic identity '" + id + "' already exists");
    }
  }

  /**
   * Reject an identifier already present in semantic topology.
   *
   * @param id proposed identifier
   */
  private void checkNewSemanticIdentity(String id) {
    String checked = requireText(id, "id");
    if (nodes.containsKey(checked) || edges.containsKey(checked)) {
      throw new IllegalArgumentException("Field identity '" + checked + "' already exists");
    }
  }

  /**
   * Validate a pump or compressor efficiency before mutating the canonical topology.
   *
   * @param efficiency efficiency fraction
   * @param equipmentName equipment type for diagnostics
   */
  private static void validateEfficiency(double efficiency, String equipmentName) {
    if (!(efficiency > 0.0) || efficiency > 1.0 || !Double.isFinite(efficiency)) {
      throw new IllegalArgumentException(equipmentName + " efficiency must be finite and in (0, 1]");
    }
  }

  /**
   * Validate all semantic edge metadata before mutating the canonical hydraulic graph.
   *
   * @param equipmentTag stable engineering tag
   * @param role semantic edge role
   * @param service field service
   * @param flowDirection declared direction contract
   * @param fromPort source-node port
   * @param toPort target-node port
   */
  private static void validateEdgeMetadata(String equipmentTag, EdgeRole role, Service service,
      FlowDirection flowDirection, String fromPort, String toPort) {
    requireText(equipmentTag, "equipmentTag");
    Objects.requireNonNull(role, "role cannot be null");
    Objects.requireNonNull(service, "service cannot be null");
    Objects.requireNonNull(flowDirection, "flowDirection cannot be null");
    requireText(fromPort, "fromPort");
    requireText(toPort, "toPort");
  }

  /**
   * Require a registered node.
   *
   * @param id node identifier
   */
  private void requireRegisteredNode(String id) {
    if (!nodes.containsKey(requireText(id, "node id"))) {
      throw new IllegalArgumentException("Field node '" + id + "' is not registered");
    }
  }

  /**
   * Require a registered node or edge identity.
   *
   * @param identity field identity
   */
  private void requireSemanticIdentity(String identity) {
    String checked = requireText(identity, "identity");
    if (!nodes.containsKey(checked) && !edges.containsKey(checked)) {
      throw new IllegalArgumentException("Field identity '" + checked + "' not found");
    }
  }

  /**
   * Lazily restore the transient equipment-binding map after deserialization.
   *
   * @return mutable runtime binding map
   */
  private Map<String, ProcessEquipmentInterface> equipmentBindings() {
    if (equipmentBindings == null) {
      equipmentBindings = new LinkedHashMap<String, ProcessEquipmentInterface>();
    }
    return equipmentBindings;
  }

  /**
   * Append diagnostics from the canonical hydraulic graph.
   *
   * @param issues destination list
   */
  private void appendHydraulicIssues(List<ValidationIssue> issues) {
    for (String hydraulicIssue : hydraulicNetwork.validate()) {
      Severity severity = hydraulicIssue.startsWith("ERROR:") ? Severity.ERROR : Severity.WARNING;
      issues.add(new ValidationIssue(severity, "HYDRAULIC_DEFINITION", "", hydraulicIssue));
    }
  }

  /**
   * Append missing semantic-identity diagnostics.
   *
   * @param issues destination list
   */
  private void appendCoverageIssues(List<ValidationIssue> issues) {
    for (String nodeName : hydraulicNetwork.getNodeNames()) {
      if (!nodes.containsKey(nodeName)) {
        issues.add(new ValidationIssue(Severity.ERROR, "UNCLASSIFIED_NODE", nodeName,
            "Register a typed node identity before execution"));
      }
    }
    for (String pipeName : hydraulicNetwork.getPipeNames()) {
      if (!edges.containsKey(pipeName)) {
        issues.add(new ValidationIssue(Severity.ERROR, "UNCLASSIFIED_EDGE", pipeName,
            "Register a typed edge identity before execution"));
      }
    }
  }

  /**
   * Append port uniqueness and service-compatibility diagnostics.
   *
   * @param issues destination list
   */
  private void appendPortAndServiceIssues(List<ValidationIssue> issues) {
    Set<String> usedPorts = new HashSet<String>();
    for (FieldEdge edge : edges.values()) {
      LoopedPipeNetwork.NetworkPipe pipe = hydraulicNetwork.getPipe(edge.getId());
      checkPort(issues, usedPorts, pipe.getFromNode(), edge.getFromPort(), edge.getId());
      checkPort(issues, usedPorts, pipe.getToNode(), edge.getToPort(), edge.getId());
      checkService(issues, edge, pipe.getFromNode());
      checkService(issues, edge, pipe.getToNode());
    }
  }

  /**
   * Check one named port for unique use.
   *
   * @param issues destination list
   * @param usedPorts set of node-port pairs already used
   * @param nodeId node identifier
   * @param port port name
   * @param edgeId edge identifier
   */
  private void checkPort(List<ValidationIssue> issues, Set<String> usedPorts, String nodeId, String port,
      String edgeId) {
    String key = nodeId + "\u0000" + port;
    if (!usedPorts.add(key)) {
      issues.add(new ValidationIssue(Severity.ERROR, "DUPLICATE_PORT", edgeId,
          "Port '" + port + "' on node '" + nodeId + "' is already connected"));
    }
  }

  /**
   * Check service compatibility between an edge and an endpoint.
   *
   * @param issues destination list
   * @param edge edge identity
   * @param nodeId endpoint identifier
   */
  private void checkService(List<ValidationIssue> issues, FieldEdge edge, String nodeId) {
    FieldNode node = nodes.get(nodeId);
    if (node == null) {
      return;
    }
    if (node.getService() != Service.SHARED && edge.getService() != Service.SHARED
        && node.getService() != edge.getService()) {
      issues.add(new ValidationIssue(Severity.ERROR, "SERVICE_MISMATCH", edge.getId(), "Edge service "
          + edge.getService() + " is incompatible with node '" + nodeId + "' service " + node.getService()));
    }
  }

  /**
   * Append semantic-role versus hydraulic-element diagnostics.
   *
   * @param issues destination list
   */
  private void appendElementCompatibilityIssues(List<ValidationIssue> issues) {
    for (FieldEdge edge : edges.values()) {
      LoopedPipeNetwork.NetworkElementType elementType = hydraulicNetwork.getPipe(edge.getId()).getElementType();
      if (!isCompatible(edge.getRole(), elementType)) {
        issues.add(new ValidationIssue(Severity.ERROR, "EDGE_ROLE_ELEMENT_MISMATCH", edge.getId(),
            "Field role " + edge.getRole() + " is incompatible with hydraulic element " + elementType));
      }
    }
  }

  /**
   * Check an edge role against the hydraulic element that owns its equations.
   *
   * @param role field edge role
   * @param elementType hydraulic element type
   * @return true when the mapping is valid
   */
  private boolean isCompatible(EdgeRole role, LoopedPipeNetwork.NetworkElementType elementType) {
    if (role == EdgeRole.WELL_INFLOW) {
      return elementType == LoopedPipeNetwork.NetworkElementType.WELL_IPR;
    }
    if (role == EdgeRole.TUBING) {
      return elementType == LoopedPipeNetwork.NetworkElementType.TUBING;
    }
    if (role == EdgeRole.CHOKE) {
      return elementType == LoopedPipeNetwork.NetworkElementType.CHOKE;
    }
    if (role == EdgeRole.PUMP) {
      return elementType == LoopedPipeNetwork.NetworkElementType.PUMP;
    }
    if (role == EdgeRole.COMPRESSOR) {
      return elementType == LoopedPipeNetwork.NetworkElementType.COMPRESSOR;
    }
    return elementType == LoopedPipeNetwork.NetworkElementType.PIPE
        || elementType == LoopedPipeNetwork.NetworkElementType.MULTIPHASE_PIPE;
  }

  /**
   * Append isolated-node and disconnected-component diagnostics.
   *
   * @param issues destination list
   */
  private void appendConnectivityIssues(List<ValidationIssue> issues) {
    Map<String, Set<String>> adjacency = buildAdjacency();
    for (Map.Entry<String, Set<String>> entry : adjacency.entrySet()) {
      if (entry.getValue().isEmpty()) {
        issues.add(new ValidationIssue(Severity.ERROR, "ISOLATED_NODE", entry.getKey(),
            "Connect the node to at least one hydraulic edge"));
      }
    }
    int components = countComponents(adjacency);
    if (components > 1) {
      issues.add(new ValidationIssue(Severity.WARNING, "DISCONNECTED_COMPONENTS", "", "Topology contains " + components
          + " disconnected hydraulic components; solve them separately unless this is intentional"));
    }
  }

  /**
   * Append solver and cycle applicability diagnostics.
   *
   * @param issues destination list
   */
  private void appendSolverIssues(List<ValidationIssue> issues) {
    boolean hasCycle = hasUndirectedCycle();
    if (hasCycle && hydraulicNetwork.getSolverType() == LoopedPipeNetwork.SolverType.SEQUENTIAL) {
      issues.add(new ValidationIssue(Severity.ERROR, "CYCLE_UNSUPPORTED_BY_SOLVER", "",
          "Select HARDY_CROSS or NEWTON_RAPHSON for a looped topology"));
    }
    for (FieldEdge edge : edges.values()) {
      LoopedPipeNetwork.NetworkPipe pipe = hydraulicNetwork.getPipe(edge.getId());
      if (pipe.getHydraulicModelType() != null
          && hydraulicNetwork.getSolverType() != LoopedPipeNetwork.SolverType.NEWTON_RAPHSON) {
        issues.add(new ValidationIssue(Severity.ERROR, "EDGE_HYDRAULIC_MODEL_REQUIRES_NEWTON", edge.getId(),
            "Explicit per-edge hydraulic fidelity requires NEWTON_RAPHSON"));
      }
      if (hydraulicNetwork.isConverged() && edge.getFlowDirection() == FlowDirection.FROM_TO
          && pipe.getFlowRate() < -1.0e-10) {
        issues.add(new ValidationIssue(Severity.ERROR, "UNDECLARED_REVERSE_FLOW", edge.getId(),
            "Solved flow is opposite the declared FROM_TO operating envelope; declare BIDIRECTIONAL or correct the boundaries"));
      }
    }
  }

  /**
   * Build undirected adjacency from the canonical hydraulic endpoints.
   *
   * @return adjacency map
   */
  private Map<String, Set<String>> buildAdjacency() {
    Map<String, Set<String>> adjacency = new LinkedHashMap<String, Set<String>>();
    for (String nodeName : hydraulicNetwork.getNodeNames()) {
      adjacency.put(nodeName, new HashSet<String>());
    }
    for (String pipeName : hydraulicNetwork.getPipeNames()) {
      LoopedPipeNetwork.NetworkPipe pipe = hydraulicNetwork.getPipe(pipeName);
      Set<String> from = adjacency.get(pipe.getFromNode());
      Set<String> to = adjacency.get(pipe.getToNode());
      if (from != null && to != null) {
        from.add(pipe.getToNode());
        to.add(pipe.getFromNode());
      }
    }
    return adjacency;
  }

  /**
   * Count connected components in an undirected adjacency map.
   *
   * @param adjacency node adjacency
   * @return number of components
   */
  private int countComponents(Map<String, Set<String>> adjacency) {
    Set<String> visited = new HashSet<String>();
    int components = 0;
    for (String start : adjacency.keySet()) {
      if (!visited.add(start)) {
        continue;
      }
      components++;
      Deque<String> queue = new ArrayDeque<String>();
      queue.add(start);
      while (!queue.isEmpty()) {
        String current = queue.removeFirst();
        for (String neighbor : adjacency.get(current)) {
          if (visited.add(neighbor)) {
            queue.addLast(neighbor);
          }
        }
      }
    }
    return components;
  }

  /**
   * Detect an undirected cycle, including a parallel-edge loop.
   *
   * @return true when the hydraulic topology contains a cycle
   */
  private boolean hasUndirectedCycle() {
    Map<String, String> parent = new HashMap<String, String>();
    for (String nodeName : hydraulicNetwork.getNodeNames()) {
      parent.put(nodeName, nodeName);
    }
    for (String pipeName : hydraulicNetwork.getPipeNames()) {
      LoopedPipeNetwork.NetworkPipe pipe = hydraulicNetwork.getPipe(pipeName);
      String fromRoot = findRoot(parent, pipe.getFromNode());
      String toRoot = findRoot(parent, pipe.getToNode());
      if (fromRoot.equals(toRoot)) {
        return true;
      }
      parent.put(fromRoot, toRoot);
    }
    return false;
  }

  /**
   * Find a disjoint-set root with path compression.
   *
   * @param parent disjoint-set parent map
   * @param node node identifier
   * @return root identifier
   */
  private String findRoot(Map<String, String> parent, String node) {
    String current = parent.get(node);
    if (current == null || current.equals(node)) {
      return node;
    }
    String root = findRoot(parent, current);
    parent.put(node, root);
    return root;
  }

  /**
   * Require non-empty, already-trimmed text so identities remain stable across tools.
   *
   * @param value input text
   * @param field field name for diagnostics
   * @return validated text
   */
  private static String requireText(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " cannot be empty");
    }
    if (!value.equals(value.trim())) {
      throw new IllegalArgumentException(field + " cannot have leading or trailing whitespace");
    }
    return value;
  }
}
