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
 * Builder class for constructing a {@link ProcessModelGraph} from a
 * {@link ProcessModule}.
 *
 * <p>
 * This builder handles the complexity of combining multiple
 * {@link ProcessSystem} objects into a
 * unified graph representation, while maintaining information about the
 * hierarchical structure and
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
   * @param module the process module containing ProcessSystems and/or nested
   *               modules
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
      String moduleName = nestedModule.getName() != null ? nestedModule.getName() : "Module_" + execIndex;

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
   * Convenience method for combining multiple systems without creating a
   * ProcessModule.
   * </p>
   *
   * @param modelName name for the combined model
   * @param systems   the process systems to combine
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
   *
   * @param subSystemGraphs list of sub-system graphs to flatten
   * @return a new ProcessGraph containing all nodes and edges from all sub-systems
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
   * Detects connections between different sub-systems by analyzing stream references. This includes
   * both explicit edges within sub-systems AND implicit dependencies where one system uses
   * stream/fluid objects from another system's equipment.
   *
   * @param subSystemGraphs list of sub-system graphs to analyze
   * @param nodeToSystem mapping from nodes to their owning system names
   * @param connections list to populate with detected inter-system connections
   * @param flattenedGraph the flattened graph containing all nodes
   */
  private static void detectInterSystemConnections(
      List<ProcessModelGraph.SubSystemGraph> subSystemGraphs, Map<ProcessNode, String> nodeToSystem,
      List<ProcessModelGraph.InterSystemConnection> connections, ProcessGraph flattenedGraph) {
    // Build a map of stream objects to their producing equipment AND their
    // sub-system
    Map<Object, ProcessEquipmentInterface> streamProducers = new IdentityHashMap<>();
    Map<Object, String> streamToSystem = new IdentityHashMap<>();

    // First pass: collect all stream producers from all sub-systems
    for (ProcessModelGraph.SubSystemGraph subSystem : subSystemGraphs) {
      String systemName = subSystem.getSystemName();

      for (ProcessNode node : subSystem.getGraph().getNodes()) {
        ProcessEquipmentInterface equipment = node.getEquipment();

        // Stream produces itself
        if (equipment instanceof StreamInterface) {
          StreamInterface stream = (StreamInterface) equipment;
          streamProducers.put(equipment, equipment);
          streamToSystem.put(equipment, systemName);

          // Also track the underlying fluid/thermoSystem
          if (stream.getThermoSystem() != null) {
            streamProducers.put(stream.getThermoSystem(), equipment);
            streamToSystem.put(stream.getThermoSystem(), systemName);
          }
        }

        // Collect outlet streams from equipment
        for (ProcessEdge edge : node.getOutgoingEdges()) {
          if (edge.getStream() != null) {
            streamProducers.put(edge.getStream(), equipment);
            streamToSystem.put(edge.getStream(), systemName);

            // Also track the underlying fluid
            StreamInterface outStream = edge.getStream();
            if (outStream.getThermoSystem() != null) {
              streamProducers.put(outStream.getThermoSystem(), equipment);
              streamToSystem.put(outStream.getThermoSystem(), systemName);
            }
          }
        }

        // For separators and other equipment with multiple outlets, collect those too
        collectEquipmentOutputs(equipment, systemName, streamProducers, streamToSystem);
      }
    }

    // Second pass: check each stream to see if it uses a fluid from another system
    for (ProcessModelGraph.SubSystemGraph targetSubSystem : subSystemGraphs) {
      String targetSystemName = targetSubSystem.getSystemName();

      for (ProcessNode targetNode : targetSubSystem.getGraph().getNodes()) {
        ProcessEquipmentInterface targetEquipment = targetNode.getEquipment();

        // Check if this equipment uses a stream/fluid from another system
        if (targetEquipment instanceof StreamInterface) {
          StreamInterface stream = (StreamInterface) targetEquipment;

          // Check if the stream wraps another stream (via stream field)
          try {
            java.lang.reflect.Field streamField = findStreamField(stream.getClass());
            if (streamField != null) {
              streamField.setAccessible(true);
              Object sourceStreamObj = streamField.get(stream);
              if (sourceStreamObj instanceof StreamInterface) {
                StreamInterface sourceStream = (StreamInterface) sourceStreamObj;
                String sourceSystem = streamToSystem.get(sourceStream);
                ProcessEquipmentInterface sourceEquipment = streamProducers.get(sourceStream);

                if (sourceSystem != null && !sourceSystem.equals(targetSystemName)
                    && sourceEquipment != null) {
                  ProcessNode sourceNode = flattenedGraph.getNode(sourceEquipment);
                  ProcessNode targetFlatNode = flattenedGraph.getNode(targetEquipment);

                  if (sourceNode != null && targetFlatNode != null) {
                    ProcessEdge existingEdge = findEdge(sourceNode, targetFlatNode);
                    if (existingEdge == null) {
                      flattenedGraph.addEdge(sourceNode, targetFlatNode, stream);
                      existingEdge = findEdge(sourceNode, targetFlatNode);
                    }

                    boolean exists = connectionExists(connections, sourceNode, targetFlatNode);
                    if (!exists && existingEdge != null) {
                      connections.add(new ProcessModelGraph.InterSystemConnection(sourceSystem,
                          targetSystemName, sourceNode, targetFlatNode, existingEdge));
                    }
                  }
                }
              }
            }
          } catch (Exception e) {
            // Ignore reflection errors
          }

          // Check if the stream's fluid came from another system
          if (stream.getThermoSystem() != null) {
            Object fluid = stream.getThermoSystem();
            String sourceSystem = streamToSystem.get(fluid);
            ProcessEquipmentInterface sourceEquipment = streamProducers.get(fluid);

            if (sourceSystem != null && !sourceSystem.equals(targetSystemName)
                && sourceEquipment != null) {
              // Found a cross-system connection!
              ProcessNode sourceNode = flattenedGraph.getNode(sourceEquipment);
              ProcessNode targetFlatNode = flattenedGraph.getNode(targetEquipment);

              if (sourceNode != null && targetFlatNode != null) {
                // Add edge to flattened graph if not already present
                ProcessEdge existingEdge = findEdge(sourceNode, targetFlatNode);
                if (existingEdge == null) {
                  flattenedGraph.addEdge(sourceNode, targetFlatNode, stream);
                  existingEdge = findEdge(sourceNode, targetFlatNode);
                }

                boolean exists = connectionExists(connections, sourceNode, targetFlatNode);
                if (!exists && existingEdge != null) {
                  connections.add(new ProcessModelGraph.InterSystemConnection(sourceSystem,
                      targetSystemName, sourceNode, targetFlatNode, existingEdge));
                }
              }
            }
          }
        }

        // Check Mixer inputs for cross-system connections
        if (targetEquipment instanceof neqsim.process.equipment.mixer.MixerInterface) {
          checkMixerInputs(targetEquipment, targetSystemName, streamProducers, streamToSystem,
              flattenedGraph, connections);
        }

        // Also check edges within this sub-system that might reference external
        // equipment
        for (ProcessEdge edge : targetNode.getIncomingEdges()) {
          ProcessNode sourceNode = edge.getSource();
          String sourceSystemName = nodeToSystem.get(sourceNode);

          if (sourceSystemName != null && !sourceSystemName.equals(targetSystemName)) {
            ProcessNode flatSource = flattenedGraph.getNode(sourceNode.getEquipment());
            ProcessNode flatTarget = flattenedGraph.getNode(targetNode.getEquipment());

            if (flatSource != null && flatTarget != null) {
              ProcessEdge flatEdge = findEdge(flatSource, flatTarget);
              if (flatEdge != null) {
                // Avoid duplicates
                if (!connectionExists(connections, flatSource, flatTarget)) {
                  connections.add(new ProcessModelGraph.InterSystemConnection(sourceSystemName,
                      targetSystemName, flatSource, flatTarget, flatEdge));
                }
              }
            }
          }
        }
      }
    }
  }

  /**
   * Collect output streams from specific equipment types.
   *
   * @param equipment the equipment to collect outputs from
   * @param systemName the name of the system containing the equipment
   * @param streamProducers map to populate with stream-to-producer mappings
   * @param streamToSystem map to populate with stream-to-system mappings
   */
  private static void collectEquipmentOutputs(ProcessEquipmentInterface equipment,
      String systemName, Map<Object, ProcessEquipmentInterface> streamProducers,
      Map<Object, String> streamToSystem) {
    // Separator outputs - use reflection since interface doesn't have all methods
    if (equipment instanceof neqsim.process.equipment.separator.Separator) {
      neqsim.process.equipment.separator.Separator sep = (neqsim.process.equipment.separator.Separator) equipment;
      try {
        StreamInterface gasOut = sep.getGasOutStream();
        if (gasOut != null) {
          streamProducers.put(gasOut, equipment);
          streamToSystem.put(gasOut, systemName);
          if (gasOut.getThermoSystem() != null) {
            streamProducers.put(gasOut.getThermoSystem(), equipment);
            streamToSystem.put(gasOut.getThermoSystem(), systemName);
          }
        }
      } catch (Exception e) {
        // Ignore
      }
      try {
        StreamInterface liqOut = sep.getLiquidOutStream();
        if (liqOut != null) {
          streamProducers.put(liqOut, equipment);
          streamToSystem.put(liqOut, systemName);
          if (liqOut.getThermoSystem() != null) {
            streamProducers.put(liqOut.getThermoSystem(), equipment);
            streamToSystem.put(liqOut.getThermoSystem(), systemName);
          }
        }
      } catch (Exception e) {
        // Ignore
      }

      // ThreePhaseSeparator has additional aqueous (water) outlet
      if (equipment instanceof neqsim.process.equipment.separator.ThreePhaseSeparator) {
        neqsim.process.equipment.separator.ThreePhaseSeparator threePhaseSep = (neqsim.process.equipment.separator.ThreePhaseSeparator) equipment;
        try {
          StreamInterface waterOut = threePhaseSep.getWaterOutStream();
          if (waterOut != null) {
            streamProducers.put(waterOut, equipment);
            streamToSystem.put(waterOut, systemName);
            if (waterOut.getThermoSystem() != null) {
              streamProducers.put(waterOut.getThermoSystem(), equipment);
              streamToSystem.put(waterOut.getThermoSystem(), systemName);
            }
          }
        } catch (Exception e) {
          // Ignore
        }
      }
    }

    // Splitter outputs
    if (equipment instanceof neqsim.process.equipment.splitter.SplitterInterface) {
      neqsim.process.equipment.splitter.SplitterInterface splitter = (neqsim.process.equipment.splitter.SplitterInterface) equipment;
      for (int i = 0; i < 20; i++) {
        try {
          StreamInterface splitStream = splitter.getSplitStream(i);
          if (splitStream != null) {
            streamProducers.put(splitStream, equipment);
            streamToSystem.put(splitStream, systemName);
            if (splitStream.getThermoSystem() != null) {
              streamProducers.put(splitStream.getThermoSystem(), equipment);
              streamToSystem.put(splitStream.getThermoSystem(), systemName);
            }
          }
        } catch (Exception e) {
          break;
        }
      }
    }

    // TwoPort outlet
    if (equipment instanceof neqsim.process.equipment.TwoPortInterface) {
      neqsim.process.equipment.TwoPortInterface twoPort = (neqsim.process.equipment.TwoPortInterface) equipment;
      StreamInterface outStream = twoPort.getOutletStream();
      if (outStream != null) {
        streamProducers.put(outStream, equipment);
        streamToSystem.put(outStream, systemName);
        if (outStream.getThermoSystem() != null) {
          streamProducers.put(outStream.getThermoSystem(), equipment);
          streamToSystem.put(outStream.getThermoSystem(), systemName);
        }
      }
    }

    // Mixer outlet
    if (equipment instanceof neqsim.process.equipment.mixer.MixerInterface) {
      neqsim.process.equipment.mixer.MixerInterface mixer = (neqsim.process.equipment.mixer.MixerInterface) equipment;
      StreamInterface outStream = mixer.getOutletStream();
      if (outStream != null) {
        streamProducers.put(outStream, equipment);
        streamToSystem.put(outStream, systemName);
        if (outStream.getThermoSystem() != null) {
          streamProducers.put(outStream.getThermoSystem(), equipment);
          streamToSystem.put(outStream.getThermoSystem(), systemName);
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

  /**
   * Check if a connection already exists.
   */
  private static boolean connectionExists(List<ProcessModelGraph.InterSystemConnection> connections,
      ProcessNode source, ProcessNode target) {
    for (ProcessModelGraph.InterSystemConnection conn : connections) {
      if (conn.getSourceNode().getEquipment() == source.getEquipment()
          && conn.getTargetNode().getEquipment() == target.getEquipment()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Find the 'stream' field in a Stream class hierarchy.
   */
  private static java.lang.reflect.Field findStreamField(Class<?> clazz) {
    Class<?> current = clazz;
    while (current != null && current != Object.class) {
      try {
        return current.getDeclaredField("stream");
      } catch (NoSuchFieldException e) {
        current = current.getSuperclass();
      }
    }
    return null;
  }

  /**
   * Check Mixer inputs for cross-system connections.
   *
   * @param mixer            the mixer equipment to check
   * @param targetSystemName the name of the target process system
   * @param streamProducers  map from streams to their producing equipment
   * @param streamToSystem   map from streams to their originating system name
   * @param flattenedGraph   the flattened process graph
   * @param connections      list to populate with discovered inter-system
   *                         connections
   */
  private static void checkMixerInputs(ProcessEquipmentInterface mixer, String targetSystemName,
      Map<Object, ProcessEquipmentInterface> streamProducers, Map<Object, String> streamToSystem,
      ProcessGraph flattenedGraph, List<ProcessModelGraph.InterSystemConnection> connections) {
    // Try to access mixer streams via reflection
    try {
      java.lang.reflect.Field streamsField = findField(mixer.getClass(), "streams");
      if (streamsField != null) {
        streamsField.setAccessible(true);
        Object value = streamsField.get(mixer);
        if (value instanceof java.util.List) {
          @SuppressWarnings("unchecked")
          java.util.List<StreamInterface> streams = (java.util.List<StreamInterface>) value;
          for (StreamInterface inputStream : streams) {
            if (inputStream != null) {
              // Check if this stream came from another system
              String sourceSystem = streamToSystem.get(inputStream);
              ProcessEquipmentInterface sourceEquipment = streamProducers.get(inputStream);

              if (sourceSystem != null && !sourceSystem.equals(targetSystemName)
                  && sourceEquipment != null) {
                ProcessNode sourceNode = flattenedGraph.getNode(sourceEquipment);
                ProcessNode targetNode = flattenedGraph.getNode(mixer);

                if (sourceNode != null && targetNode != null) {
                  ProcessEdge existingEdge = findEdge(sourceNode, targetNode);
                  if (existingEdge == null) {
                    flattenedGraph.addEdge(sourceNode, targetNode, inputStream);
                    existingEdge = findEdge(sourceNode, targetNode);
                  }

                  if (!connectionExists(connections, sourceNode, targetNode)
                      && existingEdge != null) {
                    connections.add(new ProcessModelGraph.InterSystemConnection(sourceSystem,
                        targetSystemName, sourceNode, targetNode, existingEdge));
                  }
                }
              }
            }
          }
        }
      }
    } catch (Exception e) {
      // Ignore reflection errors
    }
  }

  /**
   * Find a field in class hierarchy.
   */
  private static java.lang.reflect.Field findField(Class<?> clazz, String fieldName) {
    Class<?> current = clazz;
    while (current != null && current != Object.class) {
      try {
        return current.getDeclaredField(fieldName);
      } catch (NoSuchFieldException e) {
        current = current.getSuperclass();
      }
    }
    return null;
  }
}
