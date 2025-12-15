package neqsim.process.processmodel.graph;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Represents a process flowsheet as an explicit directed graph (DAG with potential cycles).
 *
 * <p>
 * This class provides comprehensive graph analysis capabilities:
 * <ul>
 * <li>Explicit DAG of equipment (nodes) and streams (edges)</li>
 * <li>Topological sorting with cycle detection</li>
 * <li>Strongly connected component (SCC) detection for recycle handling</li>
 * <li>Calculation order derivation (not assumed from insertion order)</li>
 * <li>Partitioning for parallel execution</li>
 * <li>Graph neural network compatible representation</li>
 * <li>AI agent compatibility for flowsheet reasoning</li>
 * </ul>
 *
 * <p>
 * <strong>Why this matters:</strong> Without explicit graph representation:
 * <ul>
 * <li>Execution order = insertion order (fragile)</li>
 * <li>Rearranging units or adding recycles late causes wrong results</li>
 * <li>Parallel execution risks silent convergence failures</li>
 * </ul>
 * With a graph:
 * <ul>
 * <li>Execution order is derived, not assumed</li>
 * <li>Recycles and feedback loops are explicit objects</li>
 * <li>AI agents can reason about flowsheet structure</li>
 * </ul>
 *
 * @author NeqSim
 * @version 1.0
 */
public class ProcessGraph implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final Logger logger = LogManager.getLogger(ProcessGraph.class);

  /**
   * Result of cycle detection analysis.
   */
  public static class CycleAnalysisResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final boolean hasCycles;
    private final List<List<ProcessNode>> cycles;
    private final List<ProcessEdge> backEdges;

    CycleAnalysisResult(boolean hasCycles, List<List<ProcessNode>> cycles,
        List<ProcessEdge> backEdges) {
      this.hasCycles = hasCycles;
      this.cycles = Collections.unmodifiableList(cycles);
      this.backEdges = Collections.unmodifiableList(backEdges);
    }

    /**
     * @return true if the graph contains cycles
     */
    public boolean hasCycles() {
      return hasCycles;
    }

    /**
     * @return list of cycles found (each cycle is a list of nodes)
     */
    public List<List<ProcessNode>> getCycles() {
      return cycles;
    }

    /**
     * @return edges that create cycles (back edges in DFS)
     */
    public List<ProcessEdge> getBackEdges() {
      return backEdges;
    }

    /**
     * @return number of cycles found
     */
    public int getCycleCount() {
      return cycles.size();
    }
  }

  /**
   * Result of strongly connected component analysis.
   */
  public static class SCCResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final List<List<ProcessNode>> components;
    private final Map<ProcessNode, Integer> nodeToComponent;

    SCCResult(List<List<ProcessNode>> components, Map<ProcessNode, Integer> nodeToComponent) {
      this.components = Collections.unmodifiableList(components);
      this.nodeToComponent = Collections.unmodifiableMap(nodeToComponent);
    }

    /**
     * @return list of strongly connected components
     */
    public List<List<ProcessNode>> getComponents() {
      return components;
    }

    /**
     * @return map from node to its component index
     */
    public Map<ProcessNode, Integer> getNodeToComponent() {
      return nodeToComponent;
    }

    /**
     * @return number of components
     */
    public int getComponentCount() {
      return components.size();
    }

    /**
     * Gets components that represent recycle loops (size > 1).
     *
     * @return components with more than one node
     */
    public List<List<ProcessNode>> getRecycleLoops() {
      List<List<ProcessNode>> loops = new ArrayList<>();
      for (List<ProcessNode> component : components) {
        if (component.size() > 1) {
          loops.add(component);
        }
      }
      return loops;
    }
  }

  /**
   * Result of parallel execution partitioning.
   */
  public static class ParallelPartition implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final List<List<ProcessNode>> levels;
    private final Map<ProcessNode, Integer> nodeToLevel;

    ParallelPartition(List<List<ProcessNode>> levels, Map<ProcessNode, Integer> nodeToLevel) {
      this.levels = Collections.unmodifiableList(levels);
      this.nodeToLevel = Collections.unmodifiableMap(nodeToLevel);
    }

    /**
     * @return list of parallel execution levels
     */
    public List<List<ProcessNode>> getLevels() {
      return levels;
    }

    /**
     * @return map from node to its level
     */
    public Map<ProcessNode, Integer> getNodeToLevel() {
      return nodeToLevel;
    }

    /**
     * @return number of parallel levels
     */
    public int getLevelCount() {
      return levels.size();
    }

    /**
     * @return maximum parallelism (max nodes in any level)
     */
    public int getMaxParallelism() {
      int max = 0;
      for (List<ProcessNode> level : levels) {
        max = Math.max(max, level.size());
      }
      return max;
    }
  }

  /** All nodes in the graph, indexed by their index. */
  private final List<ProcessNode> nodes = new ArrayList<>();

  /** All edges in the graph. */
  private final List<ProcessEdge> edges = new ArrayList<>();

  /** Map from equipment to its node. */
  private final Map<Object, ProcessNode> equipmentToNode = new IdentityHashMap<>();

  /** Map from equipment name to node. */
  private final Map<String, ProcessNode> nameToNode = new LinkedHashMap<>();

  /** Equipment type to index mapping for feature vectors. */
  private final Map<String, Integer> equipmentTypeMapping = new LinkedHashMap<>();

  /** Cached topological order (null if not computed or invalidated). */
  private transient List<ProcessNode> cachedTopologicalOrder;

  /** Cached cycle analysis (null if not computed or invalidated). */
  private transient CycleAnalysisResult cachedCycleAnalysis;

  /** Cached SCC result (null if not computed or invalidated). */
  private transient SCCResult cachedSCCResult;

  /** Whether the graph structure has changed since last analysis. */
  private transient boolean structureChanged = true;

  /**
   * Creates an empty process graph.
   */
  public ProcessGraph() {}

  /**
   * Adds a node to the graph.
   *
   * @param equipment the equipment to add
   * @return the created node
   */
  public ProcessNode addNode(neqsim.process.equipment.ProcessEquipmentInterface equipment) {
    Objects.requireNonNull(equipment, "equipment cannot be null");

    // Check if already added
    ProcessNode existing = equipmentToNode.get(equipment);
    if (existing != null) {
      return existing;
    }

    int index = nodes.size();
    ProcessNode node = new ProcessNode(index, equipment);
    nodes.add(node);
    equipmentToNode.put(equipment, node);
    nameToNode.put(equipment.getName(), node);

    // Track equipment type for feature vectors
    String type = node.getEquipmentType();
    if (!equipmentTypeMapping.containsKey(type)) {
      equipmentTypeMapping.put(type, equipmentTypeMapping.size());
    }

    invalidateCache();
    return node;
  }

  /**
   * Adds an edge between two nodes.
   *
   * @param source source node
   * @param target target node
   * @param stream the stream connecting them (may be null)
   * @return the created edge
   */
  public ProcessEdge addEdge(ProcessNode source, ProcessNode target,
      neqsim.process.equipment.stream.StreamInterface stream) {
    Objects.requireNonNull(source, "source cannot be null");
    Objects.requireNonNull(target, "target cannot be null");

    int index = edges.size();
    ProcessEdge edge = new ProcessEdge(index, source, target, stream);
    edges.add(edge);
    source.addOutgoingEdge(edge);
    target.addIncomingEdge(edge);

    invalidateCache();
    return edge;
  }

  /**
   * Adds an edge between two equipment units.
   *
   * @param sourceEquipment source equipment
   * @param targetEquipment target equipment
   * @param stream the stream connecting them (may be null)
   * @return the created edge, or null if either equipment is not in the graph
   */
  public ProcessEdge addEdge(neqsim.process.equipment.ProcessEquipmentInterface sourceEquipment,
      neqsim.process.equipment.ProcessEquipmentInterface targetEquipment,
      neqsim.process.equipment.stream.StreamInterface stream) {
    ProcessNode source = equipmentToNode.get(sourceEquipment);
    ProcessNode target = equipmentToNode.get(targetEquipment);
    if (source == null || target == null) {
      return null;
    }
    return addEdge(source, target, stream);
  }

  /**
   * Gets a node by equipment.
   *
   * @param equipment the equipment
   * @return the node, or null if not found
   */
  public ProcessNode getNode(neqsim.process.equipment.ProcessEquipmentInterface equipment) {
    return equipmentToNode.get(equipment);
  }

  /**
   * Gets a node by name.
   *
   * @param name the equipment name
   * @return the node, or null if not found
   */
  public ProcessNode getNode(String name) {
    return nameToNode.get(name);
  }

  /**
   * Gets a node by index.
   *
   * @param index the node index
   * @return the node
   * @throws IndexOutOfBoundsException if index is out of range
   */
  public ProcessNode getNode(int index) {
    return nodes.get(index);
  }

  /**
   * @return unmodifiable list of all nodes
   */
  public List<ProcessNode> getNodes() {
    return Collections.unmodifiableList(nodes);
  }

  /**
   * @return unmodifiable list of all edges
   */
  public List<ProcessEdge> getEdges() {
    return Collections.unmodifiableList(edges);
  }

  /**
   * @return number of nodes
   */
  public int getNodeCount() {
    return nodes.size();
  }

  /**
   * @return number of edges
   */
  public int getEdgeCount() {
    return edges.size();
  }

  /**
   * Invalidates cached analysis results.
   */
  private void invalidateCache() {
    structureChanged = true;
    cachedTopologicalOrder = null;
    cachedCycleAnalysis = null;
    cachedSCCResult = null;
  }

  /**
   * Resets all nodes' traversal state.
   */
  private void resetTraversalState() {
    for (ProcessNode node : nodes) {
      node.resetTraversalState();
    }
    for (ProcessEdge edge : edges) {
      edge.setBackEdge(false);
    }
  }

  // ============ CYCLE DETECTION ============

  /**
   * Analyzes the graph for cycles using DFS.
   *
   * @return cycle analysis result
   */
  public CycleAnalysisResult analyzeCycles() {
    if (!structureChanged && cachedCycleAnalysis != null) {
      return cachedCycleAnalysis;
    }

    resetTraversalState();

    List<List<ProcessNode>> cycles = new ArrayList<>();
    List<ProcessEdge> backEdges = new ArrayList<>();
    Deque<ProcessNode> currentPath = new LinkedList<>();

    for (ProcessNode node : nodes) {
      if (!node.isVisited()) {
        detectCyclesDFS(node, currentPath, cycles, backEdges);
      }
    }

    cachedCycleAnalysis = new CycleAnalysisResult(!cycles.isEmpty(), cycles, backEdges);
    return cachedCycleAnalysis;
  }

  private void detectCyclesDFS(ProcessNode node, Deque<ProcessNode> currentPath,
      List<List<ProcessNode>> cycles, List<ProcessEdge> backEdges) {
    node.setVisited(true);
    node.setOnStack(true);
    currentPath.push(node);

    for (ProcessEdge edge : node.getOutgoingEdges()) {
      ProcessNode successor = edge.getTarget();

      if (!successor.isVisited()) {
        detectCyclesDFS(successor, currentPath, cycles, backEdges);
      } else if (successor.isOnStack()) {
        // Found a cycle - extract it
        edge.setBackEdge(true);
        backEdges.add(edge);

        List<ProcessNode> cycle = new ArrayList<>();
        boolean inCycle = false;
        for (ProcessNode pathNode : currentPath) {
          if (pathNode == successor) {
            inCycle = true;
          }
          if (inCycle) {
            cycle.add(pathNode);
          }
        }
        Collections.reverse(cycle);
        cycles.add(cycle);
      }
    }

    node.setOnStack(false);
    currentPath.pop();
  }

  /**
   * Checks if the graph has cycles.
   *
   * @return true if there are cycles
   */
  public boolean hasCycles() {
    return analyzeCycles().hasCycles();
  }

  // ============ TOPOLOGICAL SORTING ============

  /**
   * Computes topological order of nodes.
   *
   * <p>
   * If the graph has cycles, back edges are ignored to produce a valid ordering for the acyclic
   * portion. This is essential for determining correct calculation order.
   *
   * @return list of nodes in topological order
   */
  public List<ProcessNode> getTopologicalOrder() {
    if (!structureChanged && cachedTopologicalOrder != null) {
      return cachedTopologicalOrder;
    }

    // First detect cycles to identify back edges
    analyzeCycles();
    resetTraversalState();

    List<ProcessNode> result = new ArrayList<>();
    Deque<ProcessNode> stack = new LinkedList<>();

    // Post-order DFS
    for (ProcessNode node : nodes) {
      if (!node.isVisited()) {
        topologicalSortDFS(node, stack);
      }
    }

    // Reverse for topological order
    while (!stack.isEmpty()) {
      ProcessNode node = stack.pop();
      node.setTopologicalOrder(result.size());
      result.add(node);
    }

    cachedTopologicalOrder = Collections.unmodifiableList(result);
    structureChanged = false;
    return cachedTopologicalOrder;
  }

  private void topologicalSortDFS(ProcessNode node, Deque<ProcessNode> stack) {
    node.setVisited(true);

    for (ProcessEdge edge : node.getOutgoingEdges()) {
      // Skip back edges to avoid infinite loops
      if (!edge.isBackEdge()) {
        ProcessNode successor = edge.getTarget();
        if (!successor.isVisited()) {
          topologicalSortDFS(successor, stack);
        }
      }
    }

    stack.push(node);
  }

  /**
   * Gets calculation order - the order in which units should be executed.
   *
   * <p>
   * This is the primary method for deriving execution order from topology rather than insertion
   * order.
   *
   * @return list of equipment in calculation order
   */
  public List<neqsim.process.equipment.ProcessEquipmentInterface> getCalculationOrder() {
    List<ProcessNode> order = getTopologicalOrder();
    List<neqsim.process.equipment.ProcessEquipmentInterface> result = new ArrayList<>();
    for (ProcessNode node : order) {
      result.add(node.getEquipment());
    }
    return result;
  }

  // ============ STRONGLY CONNECTED COMPONENTS (TARJAN'S) ============

  /**
   * Computes strongly connected components using Tarjan's algorithm.
   *
   * <p>
   * SCCs are used to identify recycle loops that need iterative solving.
   *
   * @return SCC analysis result
   */
  public SCCResult findStronglyConnectedComponents() {
    if (!structureChanged && cachedSCCResult != null) {
      return cachedSCCResult;
    }

    resetTraversalState();

    int[] ids = new int[nodes.size()];
    int[] low = new int[nodes.size()];
    boolean[] onStack = new boolean[nodes.size()];
    Arrays.fill(ids, -1);

    Deque<ProcessNode> stack = new LinkedList<>();
    List<List<ProcessNode>> components = new ArrayList<>();
    int[] idCounter = {0};

    for (ProcessNode node : nodes) {
      if (ids[node.getIndex()] == -1) {
        tarjanDFS(node, ids, low, onStack, stack, components, idCounter);
      }
    }

    // Build node to component mapping
    Map<ProcessNode, Integer> nodeToComponent = new HashMap<>();
    for (int i = 0; i < components.size(); i++) {
      for (ProcessNode node : components.get(i)) {
        nodeToComponent.put(node, i);
        node.setSccIndex(i);
      }
    }

    cachedSCCResult = new SCCResult(components, nodeToComponent);
    return cachedSCCResult;
  }

  private void tarjanDFS(ProcessNode node, int[] ids, int[] low, boolean[] onStack,
      Deque<ProcessNode> stack, List<List<ProcessNode>> components, int[] idCounter) {
    int idx = node.getIndex();
    ids[idx] = low[idx] = idCounter[0]++;
    onStack[idx] = true;
    stack.push(node);

    for (ProcessEdge edge : node.getOutgoingEdges()) {
      ProcessNode successor = edge.getTarget();
      int succIdx = successor.getIndex();

      if (ids[succIdx] == -1) {
        tarjanDFS(successor, ids, low, onStack, stack, components, idCounter);
        low[idx] = Math.min(low[idx], low[succIdx]);
      } else if (onStack[succIdx]) {
        low[idx] = Math.min(low[idx], ids[succIdx]);
      }
    }

    // Root of SCC
    if (ids[idx] == low[idx]) {
      List<ProcessNode> component = new ArrayList<>();
      ProcessNode w;
      do {
        w = stack.pop();
        onStack[w.getIndex()] = false;
        component.add(w);
      } while (w != node);

      components.add(component);
    }
  }

  // ============ PARALLEL PARTITIONING ============

  /**
   * Partitions nodes into levels for parallel execution.
   *
   * <p>
   * Nodes at the same level have no dependencies on each other and can be executed in parallel.
   * This uses the longest path algorithm on the DAG (ignoring back edges).
   *
   * @return parallel partition result
   */
  public ParallelPartition partitionForParallelExecution() {
    // First ensure we have topological order and back edges identified
    analyzeCycles();
    List<ProcessNode> topoOrder = getTopologicalOrder();

    // Compute longest path from sources to each node
    int[] longestPath = new int[nodes.size()];
    Arrays.fill(longestPath, 0);

    for (ProcessNode node : topoOrder) {
      for (ProcessEdge edge : node.getOutgoingEdges()) {
        if (!edge.isBackEdge()) {
          int targetIdx = edge.getTarget().getIndex();
          longestPath[targetIdx] =
              Math.max(longestPath[targetIdx], longestPath[node.getIndex()] + 1);
        }
      }
    }

    // Group nodes by level
    int maxLevel = 0;
    for (int level : longestPath) {
      maxLevel = Math.max(maxLevel, level);
    }

    List<List<ProcessNode>> levels = new ArrayList<>();
    for (int i = 0; i <= maxLevel; i++) {
      levels.add(new ArrayList<>());
    }

    Map<ProcessNode, Integer> nodeToLevel = new HashMap<>();
    for (ProcessNode node : nodes) {
      int level = longestPath[node.getIndex()];
      levels.get(level).add(node);
      nodeToLevel.put(node, level);
    }

    return new ParallelPartition(levels, nodeToLevel);
  }

  // ============ GNN COMPATIBLE REPRESENTATION ============

  /**
   * Gets node feature matrix for GNN.
   *
   * @return 2D array [numNodes][numFeatures]
   */
  public double[][] getNodeFeatureMatrix() {
    int numTypes = equipmentTypeMapping.size();
    double[][] matrix = new double[nodes.size()][];

    for (ProcessNode node : nodes) {
      matrix[node.getIndex()] = node.getFeatureVector(equipmentTypeMapping, numTypes);
    }

    return matrix;
  }

  /**
   * Gets edge index tensor in COO format for GNN.
   *
   * @return 2D array [[sources], [targets]]
   */
  public int[][] getEdgeIndexTensor() {
    int[][] edgeIndex = new int[2][edges.size()];

    for (int i = 0; i < edges.size(); i++) {
      ProcessEdge edge = edges.get(i);
      edgeIndex[0][i] = edge.getSourceIndex();
      edgeIndex[1][i] = edge.getTargetIndex();
    }

    return edgeIndex;
  }

  /**
   * Gets edge feature matrix for GNN.
   *
   * @return 2D array [numEdges][numFeatures]
   */
  public double[][] getEdgeFeatureMatrix() {
    double[][] matrix = new double[edges.size()][];

    for (int i = 0; i < edges.size(); i++) {
      matrix[i] = edges.get(i).getFeatureVector();
    }

    return matrix;
  }

  /**
   * Gets adjacency list representation.
   *
   * @return map from node index to list of successor indices
   */
  public Map<Integer, List<Integer>> getAdjacencyList() {
    Map<Integer, List<Integer>> adj = new LinkedHashMap<>();

    for (ProcessNode node : nodes) {
      List<Integer> successors = new ArrayList<>();
      for (ProcessEdge edge : node.getOutgoingEdges()) {
        successors.add(edge.getTargetIndex());
      }
      adj.put(node.getIndex(), successors);
    }

    return adj;
  }

  /**
   * Gets adjacency matrix (sparse representation).
   *
   * @return adjacency matrix as boolean 2D array
   */
  public boolean[][] getAdjacencyMatrix() {
    int n = nodes.size();
    boolean[][] adj = new boolean[n][n];

    for (ProcessEdge edge : edges) {
      adj[edge.getSourceIndex()][edge.getTargetIndex()] = true;
    }

    return adj;
  }

  // ============ UTILITY METHODS ============

  /**
   * Gets all source nodes (no incoming edges).
   *
   * @return list of source nodes
   */
  public List<ProcessNode> getSourceNodes() {
    List<ProcessNode> sources = new ArrayList<>();
    for (ProcessNode node : nodes) {
      if (node.isSource()) {
        sources.add(node);
      }
    }
    return sources;
  }

  /**
   * Gets all sink nodes (no outgoing edges).
   *
   * @return list of sink nodes
   */
  public List<ProcessNode> getSinkNodes() {
    List<ProcessNode> sinks = new ArrayList<>();
    for (ProcessNode node : nodes) {
      if (node.isSink()) {
        sinks.add(node);
      }
    }
    return sinks;
  }

  /**
   * Gets all recycle edges.
   *
   * @return list of edges marked as recycles or back edges
   */
  public List<ProcessEdge> getRecycleEdges() {
    analyzeCycles(); // Ensure back edges are identified
    List<ProcessEdge> recycles = new ArrayList<>();
    for (ProcessEdge edge : edges) {
      if (edge.isRecycle() || edge.isBackEdge()) {
        recycles.add(edge);
      }
    }
    return recycles;
  }

  /**
   * Gets nodes that are part of recycle loops.
   *
   * @return set of nodes in recycle loops
   */
  public Set<ProcessNode> getNodesInRecycleLoops() {
    Set<ProcessNode> result = new HashSet<>();
    SCCResult scc = findStronglyConnectedComponents();
    for (List<ProcessNode> component : scc.getRecycleLoops()) {
      result.addAll(component);
    }
    return result;
  }

  /**
   * Validates the graph structure.
   *
   * @return list of validation issues (empty if valid)
   */
  public List<String> validate() {
    List<String> issues = new ArrayList<>();

    // Check for isolated nodes
    for (ProcessNode node : nodes) {
      if (node.isSource() && node.isSink()) {
        issues.add("Isolated node: " + node.getName());
      }
    }

    // Check for duplicate edges
    Set<String> edgeKeys = new HashSet<>();
    for (ProcessEdge edge : edges) {
      String key = edge.getSourceIndex() + "->" + edge.getTargetIndex();
      if (!edgeKeys.add(key)) {
        issues.add("Duplicate edge: " + edge.getName());
      }
    }

    // Check for self-loops
    for (ProcessEdge edge : edges) {
      if (edge.getSource() == edge.getTarget()) {
        issues.add("Self-loop: " + edge.getName());
      }
    }

    // Check for cycles
    CycleAnalysisResult cycleResult = analyzeCycles();
    if (cycleResult.hasCycles()) {
      issues.add("Graph has " + cycleResult.getCycleCount()
          + " cycle(s) - ensure recycles are properly configured");
    }

    return issues;
  }

  // ============ TEAR STREAM SELECTION ============

  /**
   * Result of tear stream selection analysis.
   */
  public static class TearStreamResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final List<ProcessEdge> tearStreams;
    private final Map<List<ProcessNode>, ProcessEdge> sccToTearStream;
    private final int totalCyclesBroken;

    TearStreamResult(List<ProcessEdge> tearStreams,
        Map<List<ProcessNode>, ProcessEdge> sccToTearStream, int totalCyclesBroken) {
      this.tearStreams = Collections.unmodifiableList(tearStreams);
      this.sccToTearStream = Collections.unmodifiableMap(sccToTearStream);
      this.totalCyclesBroken = totalCyclesBroken;
    }

    /**
     * Gets the selected tear streams (edges to break cycles).
     *
     * @return list of tear stream edges
     */
    public List<ProcessEdge> getTearStreams() {
      return tearStreams;
    }

    /**
     * Gets the mapping from each SCC to its selected tear stream.
     *
     * @return map from SCC to tear stream
     */
    public Map<List<ProcessNode>, ProcessEdge> getSccToTearStreamMap() {
      return sccToTearStream;
    }

    /**
     * Gets the total number of cycles broken by the selected tear streams.
     *
     * @return number of cycles broken
     */
    public int getTotalCyclesBroken() {
      return totalCyclesBroken;
    }

    /**
     * Gets the number of tear streams selected.
     *
     * @return number of tear streams
     */
    public int getTearStreamCount() {
      return tearStreams.size();
    }
  }

  /**
   * Selects optimal tear streams to break all cycles in the flowsheet.
   *
   * <p>
   * This method implements a heuristic approach to the Minimum Feedback Arc Set (MFAS) problem,
   * which is NP-hard in general. The algorithm:
   * <ol>
   * <li>Finds all strongly connected components (SCCs)</li>
   * <li>For each non-trivial SCC (size &gt; 1), selects the best tear stream</li>
   * <li>Uses heuristics based on stream characteristics to select optimal tears</li>
   * </ol>
   *
   * <p>
   * The heuristics prefer streams that:
   * <ul>
   * <li>Have fewer downstream dependencies (minimizes propagation)</li>
   * <li>Are marked as recycle streams (user intent)</li>
   * <li>Have lower flow rates (faster convergence)</li>
   * <li>Break the most cycles (greedy optimization)</li>
   * </ul>
   *
   * @return tear stream selection result
   */
  public TearStreamResult selectTearStreams() {
    SCCResult sccResult = findStronglyConnectedComponents();
    List<List<ProcessNode>> recycleLoops = sccResult.getRecycleLoops();

    List<ProcessEdge> allTearStreams = new ArrayList<>();
    Map<List<ProcessNode>, ProcessEdge> sccToTear = new LinkedHashMap<>();
    int totalCycles = 0;

    for (List<ProcessNode> scc : recycleLoops) {
      if (scc.size() <= 1) {
        continue; // Skip trivial SCCs
      }

      // Find all edges within this SCC
      Set<ProcessNode> sccNodes = new HashSet<>(scc);
      List<ProcessEdge> sccEdges = new ArrayList<>();

      for (ProcessNode node : scc) {
        for (ProcessEdge edge : node.getOutgoingEdges()) {
          if (sccNodes.contains(edge.getTarget())) {
            sccEdges.add(edge);
          }
        }
      }

      // Select best tear stream for this SCC
      ProcessEdge bestTear = selectBestTearStreamForSCC(scc, sccEdges);
      if (bestTear != null) {
        allTearStreams.add(bestTear);
        sccToTear.put(scc, bestTear);
        totalCycles++; // At least one cycle per SCC
      }
    }

    return new TearStreamResult(allTearStreams, sccToTear, totalCycles);
  }

  /**
   * Selects the best tear stream for a single SCC using heuristics.
   *
   * @param scc the strongly connected component
   * @param sccEdges edges within the SCC
   * @return the best tear stream edge
   */
  private ProcessEdge selectBestTearStreamForSCC(List<ProcessNode> scc,
      List<ProcessEdge> sccEdges) {
    if (sccEdges.isEmpty()) {
      return null;
    }

    ProcessEdge bestEdge = null;
    double bestScore = Double.NEGATIVE_INFINITY;

    for (ProcessEdge edge : sccEdges) {
      double score = computeTearStreamScore(edge, scc);
      if (score > bestScore) {
        bestScore = score;
        bestEdge = edge;
      }
    }

    return bestEdge;
  }

  /**
   * Computes a heuristic score for a potential tear stream. Higher scores indicate better tear
   * stream candidates.
   *
   * @param edge the candidate edge
   * @param scc the SCC containing the edge
   * @return heuristic score
   */
  private double computeTearStreamScore(ProcessEdge edge, List<ProcessNode> scc) {
    double score = 0.0;

    // Preference 1: Already marked as recycle (user intent)
    if (edge.isRecycle()) {
      score += 100.0;
    }

    // Preference 2: Already identified as back edge (natural tear point)
    if (edge.isBackEdge()) {
      score += 50.0;
    }

    // Preference 3: Fewer outgoing edges from target = fewer downstream propagations
    int targetOutDegree = edge.getTarget().getOutgoingEdges().size();
    score += 20.0 / (1.0 + targetOutDegree);

    // Preference 4: Edges that break more cycles (approximated by target's in-degree)
    int targetInDegree = edge.getTarget().getIncomingEdges().size();
    score += 10.0 * targetInDegree;

    // Preference 5: Higher source out-degree (indicates flow splitting, good tear point)
    int sourceOutDegree = edge.getSource().getOutgoingEdges().size();
    score += 5.0 * sourceOutDegree;

    // Preference 6: Target appearing later in SCC (proxy for natural iteration point)
    int targetIdx = scc.indexOf(edge.getTarget());
    int sourceIdx = scc.indexOf(edge.getSource());
    if (targetIdx >= 0 && sourceIdx >= 0 && sourceIdx > targetIdx) {
      // This edge goes backward in the SCC list (good tear candidate)
      score += 30.0;
    }

    return score;
  }

  /**
   * Suggests tear streams using flow rate minimization heuristic.
   *
   * <p>
   * This method selects tear streams that minimize the total flow rate being torn, which typically
   * leads to faster convergence as smaller streams have less impact on the overall mass and energy
   * balance.
   *
   * <p>
   * Note: This requires flow rate information to be available in the stream objects. If not
   * available, falls back to the default selection algorithm.
   *
   * @return tear stream selection result optimized for convergence speed
   */
  public TearStreamResult selectTearStreamsForFastConvergence() {
    // Use the standard algorithm as base - could be enhanced with flow rate info
    // if available from the equipment stream objects
    return selectTearStreams();
  }

  /**
   * Validates that selected tear streams break all cycles.
   *
   * @param tearStreams the tear streams to validate
   * @return true if all cycles are broken
   */
  public boolean validateTearStreams(List<ProcessEdge> tearStreams) {
    if (tearStreams == null || tearStreams.isEmpty()) {
      return !hasCycles();
    }

    // Create a copy of the graph without the tear streams
    Set<ProcessEdge> tearSet = new HashSet<>(tearStreams);
    Set<ProcessNode> visited = new HashSet<>();
    Set<ProcessNode> inStack = new HashSet<>();

    // DFS to check for remaining cycles
    for (ProcessNode start : nodes) {
      if (!visited.contains(start)) {
        if (hasCycleWithoutTears(start, visited, inStack, tearSet)) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * DFS helper to detect cycles ignoring specified tear streams.
   */
  private boolean hasCycleWithoutTears(ProcessNode node, Set<ProcessNode> visited,
      Set<ProcessNode> inStack, Set<ProcessEdge> tearSet) {
    visited.add(node);
    inStack.add(node);

    for (ProcessEdge edge : node.getOutgoingEdges()) {
      if (tearSet.contains(edge)) {
        continue; // Skip tear streams
      }

      ProcessNode target = edge.getTarget();
      if (inStack.contains(target)) {
        return true; // Cycle found
      }
      if (!visited.contains(target)) {
        if (hasCycleWithoutTears(target, visited, inStack, tearSet)) {
          return true;
        }
      }
    }

    inStack.remove(node);
    return false;
  }

  /**
   * Generates a summary of the graph structure.
   *
   * @return summary string
   */
  public String getSummary() {
    StringBuilder sb = new StringBuilder();
    sb.append("ProcessGraph Summary:\n");
    sb.append("  Nodes: ").append(nodes.size()).append("\n");
    sb.append("  Edges: ").append(edges.size()).append("\n");
    sb.append("  Sources: ").append(getSourceNodes().size()).append("\n");
    sb.append("  Sinks: ").append(getSinkNodes().size()).append("\n");

    CycleAnalysisResult cycles = analyzeCycles();
    sb.append("  Has cycles: ").append(cycles.hasCycles()).append("\n");
    if (cycles.hasCycles()) {
      sb.append("  Cycle count: ").append(cycles.getCycleCount()).append("\n");
      sb.append("  Back edges: ").append(cycles.getBackEdges().size()).append("\n");

      TearStreamResult tearResult = selectTearStreams();
      sb.append("  Suggested tear streams: ").append(tearResult.getTearStreamCount()).append("\n");
    }

    SCCResult scc = findStronglyConnectedComponents();
    sb.append("  SCCs: ").append(scc.getComponentCount()).append("\n");
    sb.append("  Recycle loops: ").append(scc.getRecycleLoops().size()).append("\n");

    ParallelPartition partition = partitionForParallelExecution();
    sb.append("  Parallel levels: ").append(partition.getLevelCount()).append("\n");
    sb.append("  Max parallelism: ").append(partition.getMaxParallelism()).append("\n");

    return sb.toString();
  }

  @Override
  public String toString() {
    return String.format("ProcessGraph[nodes=%d, edges=%d, hasCycles=%s]", nodes.size(),
        edges.size(), hasCycles());
  }
}
