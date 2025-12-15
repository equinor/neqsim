package neqsim.process.processmodel.graph;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.processmodel.ProcessModule;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Represents a hierarchical process model as a graph of graphs.
 *
 * <p>
 * This class handles the case where multiple {@link ProcessSystem} objects are combined into a
 * {@link ProcessModule}. It maintains both:
 * <ul>
 * <li>Individual graphs for each ProcessSystem (sub-graphs)</li>
 * <li>A unified flattened graph for the entire model</li>
 * <li>A module-level graph showing inter-system connections</li>
 * </ul>
 *
 * <p>
 * <strong>Use cases:</strong>
 * <ul>
 * <li>Analyzing complex process plants with multiple interacting subsystems</li>
 * <li>Identifying cross-system stream connections</li>
 * <li>Determining calculation order across subsystems</li>
 * <li>AI/ML analysis of hierarchical process structures</li>
 * </ul>
 *
 * @author NeqSim
 * @version 1.0
 */
public class ProcessModelGraph implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final Logger logger = LogManager.getLogger(ProcessModelGraph.class);

  private final String modelName;
  private final List<SubSystemGraph> subSystemGraphs;
  private final ProcessGraph flattenedGraph;
  private final List<InterSystemConnection> interSystemConnections;

  /**
   * Represents a sub-system (ProcessSystem) within the model.
   */
  public static class SubSystemGraph implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String systemName;
    private final ProcessGraph graph;
    private final int executionIndex;
    private final boolean isModule;

    SubSystemGraph(String systemName, ProcessGraph graph, int executionIndex, boolean isModule) {
      this.systemName = systemName;
      this.graph = graph;
      this.executionIndex = executionIndex;
      this.isModule = isModule;
    }

    /**
     * @return the name of this sub-system
     */
    public String getSystemName() {
      return systemName;
    }

    /**
     * @return the graph for this sub-system
     */
    public ProcessGraph getGraph() {
      return graph;
    }

    /**
     * @return the execution index in the module (order of execution)
     */
    public int getExecutionIndex() {
      return executionIndex;
    }

    /**
     * @return true if this sub-system is itself a ProcessModule
     */
    public boolean isModule() {
      return isModule;
    }

    /**
     * @return number of nodes in this sub-system
     */
    public int getNodeCount() {
      return graph.getNodeCount();
    }

    /**
     * @return number of edges in this sub-system
     */
    public int getEdgeCount() {
      return graph.getEdgeCount();
    }
  }

  /**
   * Represents a connection between two sub-systems.
   */
  public static class InterSystemConnection implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String sourceSystemName;
    private final String targetSystemName;
    private final ProcessNode sourceNode;
    private final ProcessNode targetNode;
    private final ProcessEdge edge;

    InterSystemConnection(String sourceSystemName, String targetSystemName, ProcessNode sourceNode,
        ProcessNode targetNode, ProcessEdge edge) {
      this.sourceSystemName = sourceSystemName;
      this.targetSystemName = targetSystemName;
      this.sourceNode = sourceNode;
      this.targetNode = targetNode;
      this.edge = edge;
    }

    /**
     * @return the name of the source sub-system
     */
    public String getSourceSystemName() {
      return sourceSystemName;
    }

    /**
     * @return the name of the target sub-system
     */
    public String getTargetSystemName() {
      return targetSystemName;
    }

    /**
     * @return the source node in the source sub-system
     */
    public ProcessNode getSourceNode() {
      return sourceNode;
    }

    /**
     * @return the target node in the target sub-system
     */
    public ProcessNode getTargetNode() {
      return targetNode;
    }

    /**
     * @return the edge connecting the two nodes
     */
    public ProcessEdge getEdge() {
      return edge;
    }

    @Override
    public String toString() {
      return String.format("%s[%s] -> %s[%s]", sourceSystemName, sourceNode.getName(),
          targetSystemName, targetNode.getName());
    }
  }

  /**
   * Private constructor - use {@link ProcessModelGraphBuilder#buildModelGraph} instead.
   */
  ProcessModelGraph(String modelName, List<SubSystemGraph> subSystemGraphs,
      ProcessGraph flattenedGraph, List<InterSystemConnection> interSystemConnections) {
    this.modelName = modelName;
    this.subSystemGraphs = Collections.unmodifiableList(new ArrayList<>(subSystemGraphs));
    this.flattenedGraph = flattenedGraph;
    this.interSystemConnections =
        Collections.unmodifiableList(new ArrayList<>(interSystemConnections));
  }

  /**
   * @return the name of this process model
   */
  public String getModelName() {
    return modelName;
  }

  /**
   * @return list of sub-system graphs
   */
  public List<SubSystemGraph> getSubSystemGraphs() {
    return subSystemGraphs;
  }

  /**
   * @return the flattened graph containing all equipment from all sub-systems
   */
  public ProcessGraph getFlattenedGraph() {
    return flattenedGraph;
  }

  /**
   * @return list of connections between sub-systems
   */
  public List<InterSystemConnection> getInterSystemConnections() {
    return interSystemConnections;
  }

  /**
   * @return total number of sub-systems
   */
  public int getSubSystemCount() {
    return subSystemGraphs.size();
  }

  /**
   * @return total number of nodes across all sub-systems
   */
  public int getTotalNodeCount() {
    return flattenedGraph.getNodeCount();
  }

  /**
   * @return total number of edges across all sub-systems
   */
  public int getTotalEdgeCount() {
    return flattenedGraph.getEdgeCount();
  }

  /**
   * @return number of cross-system connections
   */
  public int getInterSystemConnectionCount() {
    return interSystemConnections.size();
  }

  /**
   * Get sub-system graph by name.
   *
   * @param systemName the name of the sub-system
   * @return the sub-system graph, or null if not found
   */
  public SubSystemGraph getSubSystem(String systemName) {
    for (SubSystemGraph subSystem : subSystemGraphs) {
      if (subSystem.getSystemName().equals(systemName)) {
        return subSystem;
      }
    }
    return null;
  }

  /**
   * Get sub-system graph by index.
   *
   * @param index the execution index
   * @return the sub-system graph, or null if not found
   */
  public SubSystemGraph getSubSystemByIndex(int index) {
    for (SubSystemGraph subSystem : subSystemGraphs) {
      if (subSystem.getExecutionIndex() == index) {
        return subSystem;
      }
    }
    return null;
  }

  /**
   * Get connections from a specific sub-system.
   *
   * @param systemName the name of the source sub-system
   * @return list of connections originating from this sub-system
   */
  public List<InterSystemConnection> getConnectionsFrom(String systemName) {
    List<InterSystemConnection> result = new ArrayList<>();
    for (InterSystemConnection conn : interSystemConnections) {
      if (conn.getSourceSystemName().equals(systemName)) {
        result.add(conn);
      }
    }
    return result;
  }

  /**
   * Get connections to a specific sub-system.
   *
   * @param systemName the name of the target sub-system
   * @return list of connections going to this sub-system
   */
  public List<InterSystemConnection> getConnectionsTo(String systemName) {
    List<InterSystemConnection> result = new ArrayList<>();
    for (InterSystemConnection conn : interSystemConnections) {
      if (conn.getTargetSystemName().equals(systemName)) {
        result.add(conn);
      }
    }
    return result;
  }

  /**
   * Check if the model has any cycles.
   *
   * @return true if any sub-system or the overall model has cycles
   */
  public boolean hasCycles() {
    return flattenedGraph.hasCycles();
  }

  /**
   * Analyzes cycles across the entire model.
   *
   * @return cycle analysis result for the flattened graph
   */
  public ProcessGraph.CycleAnalysisResult analyzeCycles() {
    return flattenedGraph.analyzeCycles();
  }

  /**
   * Get the calculation order for the entire model.
   *
   * @return list of equipment in topological order, or null if cycles exist
   */
  public List<neqsim.process.equipment.ProcessEquipmentInterface> getCalculationOrder() {
    return flattenedGraph.getCalculationOrder();
  }

  /**
   * Partition the entire model for parallel execution.
   *
   * @return parallel partition result
   */
  public ProcessGraph.ParallelPartition partitionForParallelExecution() {
    return flattenedGraph.partitionForParallelExecution();
  }

  /**
   * Get node feature matrix for the entire model (GNN compatible).
   *
   * @return 2D array [nodes][features]
   */
  public double[][] getNodeFeatureMatrix() {
    return flattenedGraph.getNodeFeatureMatrix();
  }

  /**
   * Get edge index tensor for the entire model (GNN compatible).
   *
   * @return 2D array [2][edges] with [0]=source indices, [1]=target indices
   */
  public int[][] getEdgeIndexTensor() {
    return flattenedGraph.getEdgeIndexTensor();
  }

  /**
   * Generate a human-readable summary of the model graph.
   *
   * @return summary string
   */
  public String getSummary() {
    StringBuilder sb = new StringBuilder();
    sb.append("ProcessModelGraph: ").append(modelName).append("\n");
    sb.append("=".repeat(50)).append("\n");
    sb.append("Sub-systems: ").append(subSystemGraphs.size()).append("\n");
    sb.append("Total nodes: ").append(getTotalNodeCount()).append("\n");
    sb.append("Total edges: ").append(getTotalEdgeCount()).append("\n");
    sb.append("Inter-system connections: ").append(interSystemConnections.size()).append("\n");
    sb.append("Has cycles: ").append(hasCycles()).append("\n");
    sb.append("\n");

    sb.append("Sub-system Details:\n");
    sb.append("-".repeat(50)).append("\n");
    for (SubSystemGraph subSystem : subSystemGraphs) {
      sb.append(String.format("  [%d] %s: %d nodes, %d edges%s\n", subSystem.getExecutionIndex(),
          subSystem.getSystemName(), subSystem.getNodeCount(), subSystem.getEdgeCount(),
          subSystem.isModule() ? " (module)" : ""));
    }

    if (!interSystemConnections.isEmpty()) {
      sb.append("\nInter-system Connections:\n");
      sb.append("-".repeat(50)).append("\n");
      for (InterSystemConnection conn : interSystemConnections) {
        sb.append("  ").append(conn.toString()).append("\n");
      }
    }

    // Add parallel execution analysis
    sb.append("\nParallel Execution Analysis:\n");
    sb.append("-".repeat(50)).append("\n");
    ModuleParallelPartition partition = partitionSubSystemsForParallelExecution();
    sb.append("Parallel beneficial: ").append(isParallelSubSystemExecutionBeneficial())
        .append("\n");
    sb.append("Execution levels: ").append(partition.getLevelCount()).append("\n");
    sb.append("Max parallelism: ").append(partition.getMaxParallelism()).append("\n");

    for (int i = 0; i < partition.getLevels().size(); i++) {
      List<SubSystemGraph> level = partition.getLevels().get(i);
      List<String> names = new ArrayList<>();
      for (SubSystemGraph sub : level) {
        names.add(sub.getSystemName());
      }
      String parallel = level.size() > 1 ? " [parallel]" : "";
      sb.append("  Level ").append(i).append(": ").append(String.join(", ", names)).append(parallel)
          .append("\n");
    }

    return sb.toString();
  }

  /**
   * Get a map showing which sub-system each node belongs to.
   *
   * @return map from node to sub-system name
   */
  public Map<ProcessNode, String> getNodeToSubSystemMap() {
    Map<ProcessNode, String> map = new IdentityHashMap<>();
    for (SubSystemGraph subSystem : subSystemGraphs) {
      for (ProcessNode node : subSystem.getGraph().getNodes()) {
        map.put(node, subSystem.getSystemName());
      }
    }
    return map;
  }

  /**
   * Get statistics about the model structure.
   *
   * @return map of statistic name to value
   */
  public Map<String, Object> getStatistics() {
    Map<String, Object> stats = new HashMap<>();
    stats.put("modelName", modelName);
    stats.put("subSystemCount", subSystemGraphs.size());
    stats.put("totalNodes", getTotalNodeCount());
    stats.put("totalEdges", getTotalEdgeCount());
    stats.put("interSystemConnections", interSystemConnections.size());
    stats.put("hasCycles", hasCycles());

    // Calculate average sub-system size
    if (!subSystemGraphs.isEmpty()) {
      double avgNodes =
          subSystemGraphs.stream().mapToInt(SubSystemGraph::getNodeCount).average().orElse(0);
      double avgEdges =
          subSystemGraphs.stream().mapToInt(SubSystemGraph::getEdgeCount).average().orElse(0);
      stats.put("avgNodesPerSubSystem", avgNodes);
      stats.put("avgEdgesPerSubSystem", avgEdges);
    }

    // Coupling metrics
    if (getTotalEdgeCount() > 0) {
      double couplingRatio = (double) interSystemConnections.size() / getTotalEdgeCount();
      stats.put("interSystemCouplingRatio", couplingRatio);
    }

    // Parallel execution metrics
    ModuleParallelPartition partition = partitionSubSystemsForParallelExecution();
    stats.put("parallelLevels", partition.getLevelCount());
    stats.put("maxSubSystemParallelism", partition.getMaxParallelism());
    stats.put("isParallelBeneficial", isParallelSubSystemExecutionBeneficial());

    return stats;
  }

  /**
   * Represents a partition of sub-systems into levels for parallel execution. Sub-systems at the
   * same level have no inter-dependencies and can run concurrently.
   */
  public static class ModuleParallelPartition implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final List<List<SubSystemGraph>> levels;
    private final int maxParallelism;

    ModuleParallelPartition(List<List<SubSystemGraph>> levels) {
      this.levels = Collections.unmodifiableList(levels);
      this.maxParallelism = levels.stream().mapToInt(List::size).max().orElse(0);
    }

    /**
     * @return list of execution levels, each containing sub-systems that can run in parallel
     */
    public List<List<SubSystemGraph>> getLevels() {
      return levels;
    }

    /**
     * @return number of execution levels
     */
    public int getLevelCount() {
      return levels.size();
    }

    /**
     * @return maximum number of sub-systems that can run in parallel at any level
     */
    public int getMaxParallelism() {
      return maxParallelism;
    }

    /**
     * @return list of sub-system names at each level
     */
    public List<List<String>> getLevelNames() {
      List<List<String>> result = new ArrayList<>();
      for (List<SubSystemGraph> level : levels) {
        List<String> names = new ArrayList<>();
        for (SubSystemGraph subSystem : level) {
          names.add(subSystem.getSystemName());
        }
        result.add(names);
      }
      return result;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("ModuleParallelPartition: ").append(levels.size()).append(" levels, max ")
          .append(maxParallelism).append(" parallel\n");
      for (int i = 0; i < levels.size(); i++) {
        sb.append("  Level ").append(i).append(": ");
        List<String> names = new ArrayList<>();
        for (SubSystemGraph sub : levels.get(i)) {
          names.add(sub.getSystemName());
        }
        sb.append(String.join(", ", names));
        if (levels.get(i).size() > 1) {
          sb.append(" [parallel]");
        }
        sb.append("\n");
      }
      return sb.toString();
    }
  }

  /**
   * Build a dependency graph between sub-systems based on inter-system connections.
   *
   * @return map from sub-system name to set of sub-system names it depends on
   */
  public Map<String, Set<String>> getSubSystemDependencies() {
    Map<String, Set<String>> dependencies = new HashMap<>();

    // Initialize all sub-systems with empty dependency sets
    for (SubSystemGraph subSystem : subSystemGraphs) {
      dependencies.put(subSystem.getSystemName(), new HashSet<>());
    }

    // Add dependencies based on inter-system connections
    for (InterSystemConnection conn : interSystemConnections) {
      String source = conn.getSourceSystemName();
      String target = conn.getTargetSystemName();
      // Target depends on source (needs source output as input)
      dependencies.get(target).add(source);
    }

    return dependencies;
  }

  /**
   * Partition sub-systems into levels for parallel execution. Sub-systems at the same level have no
   * dependencies between them and can execute concurrently.
   *
   * <p>
   * Uses topological sorting on the sub-system dependency graph to determine execution levels.
   *
   * @return partition of sub-systems into parallel execution levels
   */
  public ModuleParallelPartition partitionSubSystemsForParallelExecution() {
    if (subSystemGraphs.isEmpty()) {
      return new ModuleParallelPartition(Collections.emptyList());
    }

    // Build dependency graph
    Map<String, Set<String>> dependencies = getSubSystemDependencies();

    // Calculate in-degree for each sub-system
    Map<String, Integer> inDegree = new HashMap<>();
    for (SubSystemGraph subSystem : subSystemGraphs) {
      inDegree.put(subSystem.getSystemName(), dependencies.get(subSystem.getSystemName()).size());
    }

    // Map from name to SubSystemGraph
    Map<String, SubSystemGraph> nameToSubSystem = new HashMap<>();
    for (SubSystemGraph subSystem : subSystemGraphs) {
      nameToSubSystem.put(subSystem.getSystemName(), subSystem);
    }

    // Build reverse dependency map (who depends on me)
    Map<String, Set<String>> dependents = new HashMap<>();
    for (SubSystemGraph subSystem : subSystemGraphs) {
      dependents.put(subSystem.getSystemName(), new HashSet<>());
    }
    for (Map.Entry<String, Set<String>> entry : dependencies.entrySet()) {
      String target = entry.getKey();
      for (String source : entry.getValue()) {
        dependents.get(source).add(target);
      }
    }

    // Level-based topological sort (Kahn's algorithm with levels)
    List<List<SubSystemGraph>> levels = new ArrayList<>();
    Set<String> processed = new HashSet<>();

    while (processed.size() < subSystemGraphs.size()) {
      // Find all sub-systems with in-degree 0 that haven't been processed
      List<SubSystemGraph> currentLevel = new ArrayList<>();
      for (SubSystemGraph subSystem : subSystemGraphs) {
        String name = subSystem.getSystemName();
        if (!processed.contains(name) && inDegree.get(name) == 0) {
          currentLevel.add(subSystem);
        }
      }

      if (currentLevel.isEmpty()) {
        // Cycle detected - remaining sub-systems form a cycle
        logger.warn("Cycle detected in sub-system dependencies");
        // Add remaining as single level
        List<SubSystemGraph> remaining = new ArrayList<>();
        for (SubSystemGraph subSystem : subSystemGraphs) {
          if (!processed.contains(subSystem.getSystemName())) {
            remaining.add(subSystem);
          }
        }
        if (!remaining.isEmpty()) {
          levels.add(remaining);
        }
        break;
      }

      levels.add(currentLevel);

      // Update in-degrees for next level
      for (SubSystemGraph subSystem : currentLevel) {
        String name = subSystem.getSystemName();
        processed.add(name);
        for (String dependent : dependents.get(name)) {
          inDegree.put(dependent, inDegree.get(dependent) - 1);
        }
      }
    }

    return new ModuleParallelPartition(levels);
  }

  /**
   * Check if parallel execution of sub-systems would be beneficial. Returns true if there are at
   * least 2 sub-systems that can run in parallel at some level.
   *
   * @return true if parallel execution would provide speedup
   */
  public boolean isParallelSubSystemExecutionBeneficial() {
    if (subSystemGraphs.size() < 2) {
      return false;
    }

    ModuleParallelPartition partition = partitionSubSystemsForParallelExecution();
    return partition.getMaxParallelism() >= 2;
  }

  /**
   * Get the list of sub-systems that have no dependencies (can start immediately).
   *
   * @return list of independent sub-systems
   */
  public List<SubSystemGraph> getIndependentSubSystems() {
    List<SubSystemGraph> independent = new ArrayList<>();
    Map<String, Set<String>> dependencies = getSubSystemDependencies();

    for (SubSystemGraph subSystem : subSystemGraphs) {
      if (dependencies.get(subSystem.getSystemName()).isEmpty()) {
        independent.add(subSystem);
      }
    }

    return independent;
  }

  /**
   * Get sub-systems in topological order (respecting dependencies).
   *
   * @return list of sub-systems in execution order, or null if cycles exist
   */
  public List<SubSystemGraph> getSubSystemCalculationOrder() {
    ModuleParallelPartition partition = partitionSubSystemsForParallelExecution();
    List<SubSystemGraph> order = new ArrayList<>();
    for (List<SubSystemGraph> level : partition.getLevels()) {
      order.addAll(level);
    }
    return order;
  }

  @Override
  public String toString() {
    return String.format("ProcessModelGraph[%s, %d sub-systems, %d nodes, %d edges]", modelName,
        subSystemGraphs.size(), getTotalNodeCount(), getTotalEdgeCount());
  }
}
