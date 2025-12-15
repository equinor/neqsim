package neqsim.process.processmodel.graph;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessModule;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Builder class for constructing a {@link ProcessModelGraph} from a {@link ProcessModule}.
 *
 * <p>
 * This builder handles the complexity of combining multiple {@link ProcessSystem} objects into a
 * unified graph representation, while maintaining information about the hierarchical structure and
 * cross-system connections.
 * </p>
 *
 * <p>
 * Usage:
 * </p>
 *
 * <pre>
 * ProcessModule module = new ProcessModule("Plant");
 * module.add(processSystem1);
 * module.add(processSystem2);
 *
 * ProcessModelGraph modelGraph = ProcessModelGraphBuilder.buildModelGraph(module);
 *
 * // Get overall calculation order
 * List&lt;ProcessEquipmentInterface&gt; order = modelGraph.getCalculationOrder();
 *
 * // Analyze inter-system connections
 * for (InterSystemConnection conn : modelGraph.getInterSystemConnections()) {
 *   System.out.println(conn);
 * }
 * </pre>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class ProcessModelGraphBuilder {
  private static final Logger logger = LogManager.getLogger(ProcessModelGraphBuilder.class);

  private ProcessModelGraphBuilder() {
    // Static utility class
  }

  /**
   * Builds a ProcessModelGraph from a ProcessModule.
   *
   * @param module the process module containing ProcessSystems and/or nested modules
   * @return the constructed ProcessModelGraph
   */
  public static ProcessModelGraph buildModelGraph(ProcessModule module) {
    if (module == null) {
      throw new IllegalArgumentException("module cannot be null");
    }

    String modelName = module.getName();
    List<ProcessModelGraph.SubSystemGraph> subSystemGraphs = new ArrayList<>();
    List<ProcessModelGraph.InterSystemConnection> interSystemConnections = new ArrayList<>();

    // Build individual graphs for each subsystem
    List<ProcessSystem> unitOperations = module.getAddedUnitOperations();
    List<Integer> operationsIndex = module.getOperationsIndex();
    List<ProcessModule> nestedModules = module.getAddedModules();
    List<Integer> modulesIndex = module.getModulesIndex();

    // Track which nodes belong to which system
    Map<ProcessNode, String> nodeToSystem = new IdentityHashMap<>();

    // Process unit operations (ProcessSystems)
    for (int i = 0; i < unitOperations.size(); i++) {
      ProcessSystem system = unitOperations.get(i);
      int execIndex = operationsIndex.get(i);
      ProcessGraph graph = ProcessGraphBuilder.buildGraph(system);
      String systemName = system.getName() != null ? system.getName() : "System_" + execIndex;

      subSystemGraphs
          .add(new ProcessModelGraph.SubSystemGraph(systemName, graph, execIndex, false));

      // Track node ownership
      for (ProcessNode node : graph.getNodes()) {
        nodeToSystem.put(node, systemName);
      }
    }

    // Process nested modules recursively
    for (int i = 0; i < nestedModules.size(); i++) {
      ProcessModule nestedModule = nestedModules.get(i);
      int execIndex = modulesIndex.get(i);

      // Recursively build the nested module's graph
      ProcessModelGraph nestedGraph = buildModelGraph(nestedModule);

      // Add the flattened graph as a subsystem
      String moduleName =
          nestedModule.getName() != null ? nestedModule.getName() : "Module_" + execIndex;

      subSystemGraphs.add(new ProcessModelGraph.SubSystemGraph(moduleName,
          nestedGraph.getFlattenedGraph(), execIndex, true));

      // Track node ownership
      for (ProcessNode node : nestedGraph.getFlattenedGraph().getNodes()) {
        nodeToSystem.put(node, moduleName);
      }
    }

    // Build the flattened graph containing all equipment
    ProcessGraph flattenedGraph = buildFlattenedGraph(subSystemGraphs);

    // Detect inter-system connections
    detectInterSystemConnections(subSystemGraphs, nodeToSystem, interSystemConnections,
        flattenedGraph);

    return new ProcessModelGraph(modelName, subSystemGraphs, flattenedGraph,
        interSystemConnections);
  }

  /**
   * Builds a ProcessModelGraph from multiple ProcessSystems.
   *
   * <p>
   * Convenience method for combining multiple systems without creating a ProcessModule.
   * </p>
   *
   * @param modelName name for the combined model
   * @param systems the process systems to combine
   * @return the constructed ProcessModelGraph
   */
  public static ProcessModelGraph buildModelGraph(String modelName, ProcessSystem... systems) {
    if (systems == null || systems.length == 0) {
      throw new IllegalArgumentException("At least one system is required");
    }

    List<ProcessModelGraph.SubSystemGraph> subSystemGraphs = new ArrayList<>();
    List<ProcessModelGraph.InterSystemConnection> interSystemConnections = new ArrayList<>();
    Map<ProcessNode, String> nodeToSystem = new IdentityHashMap<>();

    for (int i = 0; i < systems.length; i++) {
      ProcessSystem system = systems[i];
      ProcessGraph graph = ProcessGraphBuilder.buildGraph(system);
      String systemName = system.getName() != null ? system.getName() : "System_" + i;

      subSystemGraphs.add(new ProcessModelGraph.SubSystemGraph(systemName, graph, i, false));

      for (ProcessNode node : graph.getNodes()) {
        nodeToSystem.put(node, systemName);
      }
    }

    ProcessGraph flattenedGraph = buildFlattenedGraph(subSystemGraphs);
    detectInterSystemConnections(subSystemGraphs, nodeToSystem, interSystemConnections,
        flattenedGraph);

    return new ProcessModelGraph(modelName, subSystemGraphs, flattenedGraph,
        interSystemConnections);
  }

  /**
   * Builds a flattened graph containing all nodes and edges from all sub-systems.
   */
  private static ProcessGraph buildFlattenedGraph(
      List<ProcessModelGraph.SubSystemGraph> subSystemGraphs) {
    ProcessGraph flattened = new ProcessGraph();

    // Add all nodes from all sub-systems
    for (ProcessModelGraph.SubSystemGraph subSystem : subSystemGraphs) {
      for (ProcessNode node : subSystem.getGraph().getNodes()) {
        // Re-add the equipment to the flattened graph
        flattened.addNode(node.getEquipment());
      }
    }

    // Add all edges from all sub-systems
    for (ProcessModelGraph.SubSystemGraph subSystem : subSystemGraphs) {
      for (ProcessEdge edge : subSystem.getGraph().getEdges()) {
        ProcessNode sourceInFlattened = flattened.getNode(edge.getSource().getEquipment());
        ProcessNode targetInFlattened = flattened.getNode(edge.getTarget().getEquipment());

        if (sourceInFlattened != null && targetInFlattened != null) {
          flattened.addEdge(sourceInFlattened, targetInFlattened, edge.getStream());
        }
      }
    }

    return flattened;
  }

  /**
   * Detects connections between different sub-systems by analyzing stream references.
   */
  private static void detectInterSystemConnections(
      List<ProcessModelGraph.SubSystemGraph> subSystemGraphs, Map<ProcessNode, String> nodeToSystem,
      List<ProcessModelGraph.InterSystemConnection> connections, ProcessGraph flattenedGraph) {

    // Build a map of stream objects to their producing equipment
    Map<Object, ProcessEquipmentInterface> streamProducers = new IdentityHashMap<>();

    for (ProcessModelGraph.SubSystemGraph subSystem : subSystemGraphs) {
      for (ProcessNode node : subSystem.getGraph().getNodes()) {
        ProcessEquipmentInterface equipment = node.getEquipment();

        // Stream produces itself
        if (equipment instanceof StreamInterface) {
          streamProducers.put(equipment, equipment);
        }

        // Collect outlet streams
        for (ProcessEdge edge : node.getOutgoingEdges()) {
          if (edge.getStream() != null) {
            streamProducers.put(edge.getStream(), equipment);
          }
        }
      }
    }

    // Now check each sub-system for streams that come from other sub-systems
    for (ProcessModelGraph.SubSystemGraph targetSubSystem : subSystemGraphs) {
      String targetSystemName = targetSubSystem.getSystemName();

      for (ProcessNode targetNode : targetSubSystem.getGraph().getNodes()) {
        // Check inlet edges
        for (ProcessEdge edge : targetNode.getIncomingEdges()) {
          ProcessNode sourceNode = edge.getSource();
          String sourceSystemName = nodeToSystem.get(sourceNode);

          // If source is in a different system, this is an inter-system connection
          if (sourceSystemName != null && !sourceSystemName.equals(targetSystemName)) {
            // Get the nodes in the flattened graph
            ProcessNode flatSource = flattenedGraph.getNode(sourceNode.getEquipment());
            ProcessNode flatTarget = flattenedGraph.getNode(targetNode.getEquipment());

            if (flatSource != null && flatTarget != null) {
              // Find the corresponding edge in the flattened graph
              ProcessEdge flatEdge = findEdge(flatSource, flatTarget);

              if (flatEdge != null) {
                connections.add(new ProcessModelGraph.InterSystemConnection(sourceSystemName,
                    targetSystemName, flatSource, flatTarget, flatEdge));
              }
            }
          }
        }
      }
    }
  }

  /**
   * Find an edge between two nodes.
   */
  private static ProcessEdge findEdge(ProcessNode source, ProcessNode target) {
    for (ProcessEdge edge : source.getOutgoingEdges()) {
      if (edge.getTarget() == target) {
        return edge;
      }
    }
    return null;
  }
}
