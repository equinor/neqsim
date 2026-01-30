package neqsim.process.util.topology;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Analyzes the topology (graph structure) of a process system.
 *
 * <p>
 * Extracts:
 * </p>
 * <ul>
 * <li>Functional sequence (what comes before/after)</li>
 * <li>Parallel equipment (redundant units)</li>
 * <li>Dependencies between equipment</li>
 * <li>Critical paths through the process</li>
 * </ul>
 *
 * <p>
 * This enables answering questions like:
 * </p>
 * <ul>
 * <li>"If pump A fails, what equipment is affected?"</li>
 * <li>"Which equipment can run in parallel?"</li>
 * <li>"What is the critical path through the process?"</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class ProcessTopologyAnalyzer implements Serializable {

  private static final long serialVersionUID = 1000L;
  private static final Logger logger = LogManager.getLogger(ProcessTopologyAnalyzer.class);

  private ProcessSystem processSystem;

  // Graph representation
  private Map<String, EquipmentNode> nodes;
  private List<ProcessEdge> edges;

  // Functional location mapping
  private Map<String, FunctionalLocation> functionalLocations;

  // Analysis results
  private List<List<String>> parallelGroups;
  private List<String> criticalPath;
  private Map<String, Integer> topologicalOrder;

  /**
   * Node in the process graph representing an equipment unit.
   */
  public static class EquipmentNode implements Serializable {
    private static final long serialVersionUID = 1000L;

    private String name;
    private String equipmentType;
    private FunctionalLocation functionalLocation;
    private List<String> upstreamEquipment;
    private List<String> downstreamEquipment;
    private List<String> parallelEquipment;
    private int topologicalOrder;
    private boolean isCritical;
    private double criticality; // 0-1, how critical is this equipment

    /**
     * Creates an equipment node.
     *
     * @param name equipment name
     * @param equipmentType type of equipment
     */
    public EquipmentNode(String name, String equipmentType) {
      this.name = name;
      this.equipmentType = equipmentType;
      this.upstreamEquipment = new ArrayList<>();
      this.downstreamEquipment = new ArrayList<>();
      this.parallelEquipment = new ArrayList<>();
      this.topologicalOrder = -1;
      this.isCritical = false;
      this.criticality = 0.0;
    }

    // Getters
    public String getName() {
      return name;
    }

    public String getEquipmentType() {
      return equipmentType;
    }

    public FunctionalLocation getFunctionalLocation() {
      return functionalLocation;
    }

    public void setFunctionalLocation(FunctionalLocation loc) {
      this.functionalLocation = loc;
    }

    public List<String> getUpstreamEquipment() {
      return Collections.unmodifiableList(upstreamEquipment);
    }

    public List<String> getDownstreamEquipment() {
      return Collections.unmodifiableList(downstreamEquipment);
    }

    public List<String> getParallelEquipment() {
      return Collections.unmodifiableList(parallelEquipment);
    }

    public int getTopologicalOrder() {
      return topologicalOrder;
    }

    public boolean isCritical() {
      return isCritical;
    }

    public double getCriticality() {
      return criticality;
    }

    void addUpstream(String equipment) {
      if (!upstreamEquipment.contains(equipment)) {
        upstreamEquipment.add(equipment);
      }
    }

    void addDownstream(String equipment) {
      if (!downstreamEquipment.contains(equipment)) {
        downstreamEquipment.add(equipment);
      }
    }

    void addParallel(String equipment) {
      if (!parallelEquipment.contains(equipment)) {
        parallelEquipment.add(equipment);
      }
    }

    void setTopologicalOrder(int order) {
      this.topologicalOrder = order;
    }

    void setCritical(boolean critical) {
      this.isCritical = critical;
    }

    void setCriticality(double value) {
      this.criticality = value;
    }
  }

  /**
   * Edge in the process graph representing a stream connection.
   */
  public static class ProcessEdge implements Serializable {
    private static final long serialVersionUID = 1000L;

    private String fromEquipment;
    private String toEquipment;
    private String streamName;
    private String streamType; // gas, liquid, mixed

    /**
     * Creates a process edge.
     *
     * @param from source equipment
     * @param to destination equipment
     * @param streamName name of connecting stream
     */
    public ProcessEdge(String from, String to, String streamName) {
      this.fromEquipment = from;
      this.toEquipment = to;
      this.streamName = streamName;
    }

    public String getFromEquipment() {
      return fromEquipment;
    }

    public String getToEquipment() {
      return toEquipment;
    }

    public String getStreamName() {
      return streamName;
    }

    public String getStreamType() {
      return streamType;
    }

    void setStreamType(String type) {
      this.streamType = type;
    }
  }

  /**
   * Creates a new topology analyzer for a process system.
   *
   * @param processSystem the process system to analyze
   */
  public ProcessTopologyAnalyzer(ProcessSystem processSystem) {
    this.processSystem = processSystem;
    this.nodes = new LinkedHashMap<>();
    this.edges = new ArrayList<>();
    this.functionalLocations = new HashMap<>();
    this.parallelGroups = new ArrayList<>();
    this.criticalPath = new ArrayList<>();
    this.topologicalOrder = new HashMap<>();
  }

  /**
   * Builds the process topology graph.
   */
  public void buildTopology() {
    logger.info("Building process topology...");

    // Clear previous analysis
    nodes.clear();
    edges.clear();

    // Build nodes from equipment
    List<ProcessEquipmentInterface> units = processSystem.getUnitOperations();
    for (int i = 0; i < units.size(); i++) {
      ProcessEquipmentInterface equipment = units.get(i);
      String name = equipment.getName();
      String type = equipment.getClass().getSimpleName();

      EquipmentNode node = new EquipmentNode(name, type);

      // Check if functional location is assigned
      if (functionalLocations.containsKey(name)) {
        node.setFunctionalLocation(functionalLocations.get(name));
      }

      nodes.put(name, node);
    }

    // Build edges from stream connections
    buildEdgesFromStreams();

    // Calculate topological order
    calculateTopologicalOrder();

    // Identify parallel equipment
    identifyParallelEquipment();

    // Calculate criticality
    calculateCriticality();

    logger.info("Topology built: {} nodes, {} edges", nodes.size(), edges.size());
  }

  private void buildEdgesFromStreams() {
    // Track which equipment produces which stream
    Map<String, String> streamProducers = new HashMap<>();

    // First pass: identify stream producers
    List<ProcessEquipmentInterface> allUnits = processSystem.getUnitOperations();
    for (int i = 0; i < allUnits.size(); i++) {
      ProcessEquipmentInterface equipment = allUnits.get(i);
      String equipName = equipment.getName();

      // Get outlet streams
      try {
        // Try different outlet stream methods
        StreamInterface outlet = getOutletStream(equipment);
        if (outlet != null) {
          streamProducers.put(outlet.getName(), equipName);
        }

        // Check for multiple outlets (separator)
        List<StreamInterface> outlets = getOutletStreams(equipment);
        for (StreamInterface out : outlets) {
          streamProducers.put(out.getName(), equipName);
        }
      } catch (Exception e) {
        // Equipment may not have outlets
      }
    }

    // Second pass: build edges from inlet streams
    for (int i = 0; i < allUnits.size(); i++) {
      ProcessEquipmentInterface equipment = allUnits.get(i);
      String equipName = equipment.getName();

      try {
        // Get inlet stream
        StreamInterface inlet = getInletStream(equipment);
        if (inlet != null) {
          String producer = streamProducers.get(inlet.getName());
          if (producer != null && !producer.equals(equipName)) {
            ProcessEdge edge = new ProcessEdge(producer, equipName, inlet.getName());
            edges.add(edge);

            // Update nodes
            nodes.get(producer).addDownstream(equipName);
            nodes.get(equipName).addUpstream(producer);
          }
        }

        // Check for multiple inlets
        List<StreamInterface> inlets = getInletStreams(equipment);
        for (StreamInterface in : inlets) {
          String producer = streamProducers.get(in.getName());
          if (producer != null && !producer.equals(equipName)) {
            ProcessEdge edge = new ProcessEdge(producer, equipName, in.getName());
            edges.add(edge);
            nodes.get(producer).addDownstream(equipName);
            nodes.get(equipName).addUpstream(producer);
          }
        }
      } catch (Exception e) {
        // Equipment may not have inlets
      }
    }
  }

  private StreamInterface getOutletStream(ProcessEquipmentInterface equipment) {
    try {
      java.lang.reflect.Method method = equipment.getClass().getMethod("getOutletStream");
      return (StreamInterface) method.invoke(equipment);
    } catch (Exception e) {
      try {
        java.lang.reflect.Method method = equipment.getClass().getMethod("getOutStream");
        return (StreamInterface) method.invoke(equipment);
      } catch (Exception e2) {
        return null;
      }
    }
  }

  private List<StreamInterface> getOutletStreams(ProcessEquipmentInterface equipment) {
    List<StreamInterface> outlets = new ArrayList<>();
    try {
      // Gas outlet (separator)
      java.lang.reflect.Method gasMethod = equipment.getClass().getMethod("getGasOutStream");
      StreamInterface gas = (StreamInterface) gasMethod.invoke(equipment);
      if (gas != null) {
        outlets.add(gas);
      }
    } catch (Exception e) {
      // No gas outlet
    }
    try {
      // Liquid outlet (separator)
      java.lang.reflect.Method liqMethod = equipment.getClass().getMethod("getLiquidOutStream");
      StreamInterface liq = (StreamInterface) liqMethod.invoke(equipment);
      if (liq != null) {
        outlets.add(liq);
      }
    } catch (Exception e) {
      // No liquid outlet
    }
    return outlets;
  }

  private StreamInterface getInletStream(ProcessEquipmentInterface equipment) {
    try {
      java.lang.reflect.Method method = equipment.getClass().getMethod("getInletStream");
      return (StreamInterface) method.invoke(equipment);
    } catch (Exception e) {
      try {
        java.lang.reflect.Method method = equipment.getClass().getMethod("getInStream");
        return (StreamInterface) method.invoke(equipment);
      } catch (Exception e2) {
        return null;
      }
    }
  }

  private List<StreamInterface> getInletStreams(ProcessEquipmentInterface equipment) {
    List<StreamInterface> inlets = new ArrayList<>();
    try {
      java.lang.reflect.Method method = equipment.getClass().getMethod("getFeedStream");
      StreamInterface feed = (StreamInterface) method.invoke(equipment);
      if (feed != null) {
        inlets.add(feed);
      }
    } catch (Exception e) {
      // No feed stream method
    }
    return inlets;
  }

  private void calculateTopologicalOrder() {
    // Kahn's algorithm for topological sort
    Map<String, Integer> inDegree = new HashMap<>();
    for (String node : nodes.keySet()) {
      inDegree.put(node, 0);
    }

    for (ProcessEdge edge : edges) {
      inDegree.put(edge.getToEquipment(), inDegree.get(edge.getToEquipment()) + 1);
    }

    Queue<String> queue = new LinkedList<>();
    for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
      if (entry.getValue() == 0) {
        queue.add(entry.getKey());
      }
    }

    int order = 0;
    while (!queue.isEmpty()) {
      String current = queue.poll();
      topologicalOrder.put(current, order);
      nodes.get(current).setTopologicalOrder(order);
      order++;

      for (ProcessEdge edge : edges) {
        if (edge.getFromEquipment().equals(current)) {
          String next = edge.getToEquipment();
          inDegree.put(next, inDegree.get(next) - 1);
          if (inDegree.get(next) == 0) {
            queue.add(next);
          }
        }
      }
    }
  }

  private void identifyParallelEquipment() {
    // Equipment is parallel if it has:
    // 1. Same upstream equipment
    // 2. Same downstream equipment
    // 3. Same equipment type

    for (EquipmentNode node1 : nodes.values()) {
      for (EquipmentNode node2 : nodes.values()) {
        if (node1.getName().equals(node2.getName())) {
          continue;
        }

        // Check if same type and same connections
        if (node1.getEquipmentType().equals(node2.getEquipmentType())
            && hasSameUpstream(node1, node2) && hasSameDownstream(node1, node2)) {
          node1.addParallel(node2.getName());
          node2.addParallel(node1.getName());
        }

        // Also check by functional location
        if (node1.getFunctionalLocation() != null && node2.getFunctionalLocation() != null
            && node1.getFunctionalLocation().isParallelTo(node2.getFunctionalLocation())) {
          node1.addParallel(node2.getName());
          node2.addParallel(node1.getName());
        }
      }
    }

    // Build parallel groups
    Set<String> visited = new HashSet<>();
    for (EquipmentNode node : nodes.values()) {
      if (!visited.contains(node.getName()) && !node.getParallelEquipment().isEmpty()) {
        List<String> group = new ArrayList<>();
        group.add(node.getName());
        group.addAll(node.getParallelEquipment());
        parallelGroups.add(group);
        visited.addAll(group);
      }
    }
  }

  private boolean hasSameUpstream(EquipmentNode node1, EquipmentNode node2) {
    if (node1.getUpstreamEquipment().isEmpty() && node2.getUpstreamEquipment().isEmpty()) {
      return true;
    }
    return new HashSet<>(node1.getUpstreamEquipment())
        .equals(new HashSet<>(node2.getUpstreamEquipment()));
  }

  private boolean hasSameDownstream(EquipmentNode node1, EquipmentNode node2) {
    if (node1.getDownstreamEquipment().isEmpty() && node2.getDownstreamEquipment().isEmpty()) {
      return true;
    }
    return new HashSet<>(node1.getDownstreamEquipment())
        .equals(new HashSet<>(node2.getDownstreamEquipment()));
  }

  private void calculateCriticality() {
    // Criticality based on:
    // 1. Number of downstream equipment (more = more critical)
    // 2. Whether it's on the critical path
    // 3. Whether it has parallel equipment (less critical if redundant)

    int maxDownstream = 1;
    for (EquipmentNode node : nodes.values()) {
      int downstream = countAllDownstream(node.getName());
      if (downstream > maxDownstream) {
        maxDownstream = downstream;
      }
    }

    for (EquipmentNode node : nodes.values()) {
      int downstream = countAllDownstream(node.getName());
      double baseCriticality = (double) downstream / maxDownstream;

      // Reduce criticality if has parallel equipment
      if (!node.getParallelEquipment().isEmpty()) {
        baseCriticality *= 0.5; // 50% reduction if redundant
      }

      // Mark as critical if no parallel and has downstream
      if (node.getParallelEquipment().isEmpty() && downstream > 0) {
        node.setCritical(true);
      }

      node.setCriticality(baseCriticality);
    }
  }

  private int countAllDownstream(String equipmentName) {
    Set<String> visited = new HashSet<>();
    Queue<String> queue = new LinkedList<>();
    queue.add(equipmentName);

    while (!queue.isEmpty()) {
      String current = queue.poll();
      if (visited.contains(current)) {
        continue;
      }
      visited.add(current);

      EquipmentNode node = nodes.get(current);
      if (node != null) {
        queue.addAll(node.getDownstreamEquipment());
      }
    }

    return visited.size() - 1; // Exclude self
  }

  /**
   * Assigns a functional location to an equipment unit.
   *
   * @param equipmentName the equipment name in the process
   * @param stidTag the STID tag (e.g., "1775-KA-23011A")
   */
  public void setFunctionalLocation(String equipmentName, String stidTag) {
    FunctionalLocation loc = new FunctionalLocation(stidTag);
    functionalLocations.put(equipmentName, loc);

    if (nodes.containsKey(equipmentName)) {
      nodes.get(equipmentName).setFunctionalLocation(loc);
    }
  }

  /**
   * Assigns a functional location to an equipment unit.
   *
   * @param equipmentName the equipment name
   * @param location the functional location object
   */
  public void setFunctionalLocation(String equipmentName, FunctionalLocation location) {
    functionalLocations.put(equipmentName, location);

    if (nodes.containsKey(equipmentName)) {
      nodes.get(equipmentName).setFunctionalLocation(location);
    }
  }

  /**
   * Gets all equipment affected by failure of a specific unit.
   *
   * @param equipmentName the failed equipment
   * @return list of affected equipment names
   */
  public List<String> getAffectedByFailure(String equipmentName) {
    List<String> affected = new ArrayList<>();
    EquipmentNode node = nodes.get(equipmentName);

    if (node == null) {
      return affected;
    }

    // All downstream equipment is affected
    Set<String> visited = new HashSet<>();
    Queue<String> queue = new LinkedList<>();
    queue.addAll(node.getDownstreamEquipment());

    while (!queue.isEmpty()) {
      String current = queue.poll();
      if (visited.contains(current)) {
        continue;
      }
      visited.add(current);
      affected.add(current);

      EquipmentNode currentNode = nodes.get(current);
      if (currentNode != null) {
        queue.addAll(currentNode.getDownstreamEquipment());
      }
    }

    return affected;
  }

  /**
   * Gets equipment that becomes more critical when another fails.
   *
   * <p>
   * This answers: "If X fails, what must we watch more carefully?"
   * </p>
   *
   * @param failedEquipment the equipment that failed
   * @return map of equipment name to increased criticality
   */
  public Map<String, Double> getIncreasedCriticalityOn(String failedEquipment) {
    Map<String, Double> result = new LinkedHashMap<>();
    EquipmentNode failedNode = nodes.get(failedEquipment);

    if (failedNode == null) {
      return result;
    }

    // Parallel equipment becomes more critical
    for (String parallel : failedNode.getParallelEquipment()) {
      EquipmentNode parallelNode = nodes.get(parallel);
      // Criticality doubles (or more) when redundancy is lost
      double newCriticality = Math.min(1.0, parallelNode.getCriticality() * 2.0);
      result.put(parallel, newCriticality);
    }

    // Upstream equipment that feeds multiple paths - the remaining path is critical
    for (String upstream : failedNode.getUpstreamEquipment()) {
      EquipmentNode upstreamNode = nodes.get(upstream);
      // Check other downstream paths
      for (String otherDownstream : upstreamNode.getDownstreamEquipment()) {
        if (!otherDownstream.equals(failedEquipment)) {
          EquipmentNode otherNode = nodes.get(otherDownstream);
          double newCriticality = Math.min(1.0, otherNode.getCriticality() * 1.5);
          result.put(otherDownstream, newCriticality);
        }
      }
    }

    return result;
  }

  /**
   * Gets equipment upstream of a specific unit.
   *
   * @param equipmentName the equipment name
   * @return list of upstream equipment
   */
  public List<String> getUpstreamEquipment(String equipmentName) {
    EquipmentNode node = nodes.get(equipmentName);
    if (node == null) {
      return new ArrayList<>();
    }
    return new ArrayList<>(node.getUpstreamEquipment());
  }

  /**
   * Gets equipment downstream of a specific unit.
   *
   * @param equipmentName the equipment name
   * @return list of downstream equipment
   */
  public List<String> getDownstreamEquipment(String equipmentName) {
    EquipmentNode node = nodes.get(equipmentName);
    if (node == null) {
      return new ArrayList<>();
    }
    return new ArrayList<>(node.getDownstreamEquipment());
  }

  /**
   * Gets parallel equipment for a specific unit.
   *
   * @param equipmentName the equipment name
   * @return list of parallel equipment
   */
  public List<String> getParallelEquipment(String equipmentName) {
    EquipmentNode node = nodes.get(equipmentName);
    if (node == null) {
      return new ArrayList<>();
    }
    return new ArrayList<>(node.getParallelEquipment());
  }

  /**
   * Gets all parallel equipment groups.
   *
   * @return list of parallel groups (each group is a list of equipment names)
   */
  public List<List<String>> getParallelGroups() {
    return Collections.unmodifiableList(parallelGroups);
  }

  /**
   * Gets the topological order of equipment.
   *
   * @return map of equipment name to order (0 = first in process)
   */
  public Map<String, Integer> getTopologicalOrder() {
    return Collections.unmodifiableMap(topologicalOrder);
  }

  /**
   * Gets all equipment nodes.
   *
   * @return map of name to node
   */
  public Map<String, EquipmentNode> getNodes() {
    return Collections.unmodifiableMap(nodes);
  }

  /**
   * Gets all process edges.
   *
   * @return list of edges
   */
  public List<ProcessEdge> getEdges() {
    return Collections.unmodifiableList(edges);
  }

  /**
   * Gets an equipment node by name.
   *
   * @param name equipment name
   * @return the node or null
   */
  public EquipmentNode getNode(String name) {
    return nodes.get(name);
  }

  /**
   * Exports topology as JSON.
   *
   * @return JSON string
   */
  public String toJson() {
    Map<String, Object> result = new LinkedHashMap<>();

    // Nodes
    List<Map<String, Object>> nodeList = new ArrayList<>();
    for (EquipmentNode node : nodes.values()) {
      Map<String, Object> nodeMap = new LinkedHashMap<>();
      nodeMap.put("name", node.getName());
      nodeMap.put("type", node.getEquipmentType());
      nodeMap.put("order", node.getTopologicalOrder());
      nodeMap.put("criticality", node.getCriticality());
      nodeMap.put("isCritical", node.isCritical());
      nodeMap.put("upstream", node.getUpstreamEquipment());
      nodeMap.put("downstream", node.getDownstreamEquipment());
      nodeMap.put("parallel", node.getParallelEquipment());

      if (node.getFunctionalLocation() != null) {
        nodeMap.put("stidTag", node.getFunctionalLocation().getFullTag());
        nodeMap.put("installation", node.getFunctionalLocation().getInstallationName());
      }

      nodeList.add(nodeMap);
    }
    result.put("nodes", nodeList);

    // Edges
    List<Map<String, Object>> edgeList = new ArrayList<>();
    for (ProcessEdge edge : edges) {
      Map<String, Object> edgeMap = new LinkedHashMap<>();
      edgeMap.put("from", edge.getFromEquipment());
      edgeMap.put("to", edge.getToEquipment());
      edgeMap.put("stream", edge.getStreamName());
      edgeList.add(edgeMap);
    }
    result.put("edges", edgeList);

    // Parallel groups
    result.put("parallelGroups", parallelGroups);

    return new GsonBuilder().setPrettyPrinting().create().toJson(result);
  }

  /**
   * Exports topology in DOT format for Graphviz visualization.
   *
   * @return DOT graph string
   */
  public String toDotGraph() {
    StringBuilder sb = new StringBuilder();
    sb.append("digraph ProcessTopology {\n");
    sb.append("  rankdir=LR;\n");
    sb.append("  node [shape=box, style=filled];\n\n");

    // Nodes with colors based on criticality
    for (EquipmentNode node : nodes.values()) {
      String color;
      if (node.getCriticality() > 0.7) {
        color = "#ff6666"; // Red for high criticality
      } else if (node.getCriticality() > 0.4) {
        color = "#ffcc66"; // Orange for medium
      } else {
        color = "#99ff99"; // Green for low
      }

      String label = node.getName();
      if (node.getFunctionalLocation() != null) {
        label += "\\n" + node.getFunctionalLocation().getFullTag();
      }

      sb.append(String.format("  \"%s\" [label=\"%s\", fillcolor=\"%s\"];\n", node.getName(), label,
          color));
    }

    sb.append("\n");

    // Edges
    for (ProcessEdge edge : edges) {
      sb.append(
          String.format("  \"%s\" -> \"%s\";\n", edge.getFromEquipment(), edge.getToEquipment()));
    }

    // Parallel equipment (dashed lines)
    Set<String> drawnParallel = new HashSet<>();
    for (EquipmentNode node : nodes.values()) {
      for (String parallel : node.getParallelEquipment()) {
        String key = node.getName().compareTo(parallel) < 0 ? node.getName() + "-" + parallel
            : parallel + "-" + node.getName();
        if (!drawnParallel.contains(key)) {
          sb.append(String.format("  \"%s\" -> \"%s\" [style=dashed, dir=none, color=blue];\n",
              node.getName(), parallel));
          drawnParallel.add(key);
        }
      }
    }

    sb.append("}\n");
    return sb.toString();
  }
}
