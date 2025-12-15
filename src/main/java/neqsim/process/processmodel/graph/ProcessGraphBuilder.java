package neqsim.process.processmodel.graph;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.TwoPortInterface;
import neqsim.process.equipment.expander.TurboExpanderCompressor;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.heatexchanger.MultiStreamHeatExchangerInterface;
import neqsim.process.equipment.mixer.MixerInterface;
import neqsim.process.equipment.splitter.SplitterInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Builder class for constructing a {@link ProcessGraph} from a {@link ProcessSystem}.
 *
 * <p>
 * This class discovers stream connections between equipment units and produces an explicit graph
 * structure that enables topology-based calculation order derivation.
 * </p>
 *
 * <p>
 * Usage:
 * </p>
 *
 * <pre>
 * ProcessSystem system = ...;
 * ProcessGraph graph = ProcessGraphBuilder.buildGraph(system);
 *
 * // Get calculation order derived from topology
 * List&lt;ProcessEquipmentInterface&gt; order = graph.getCalculationOrder();
 *
 * // Partition for parallel execution
 * ProcessGraph.ParallelPartition partition = graph.partitionForParallelExecution();
 * </pre>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class ProcessGraphBuilder {
  private static final Logger logger = LogManager.getLogger(ProcessGraphBuilder.class);

  private ProcessGraphBuilder() {
    // Static utility class
  }

  /**
   * Builds a process graph from a ProcessSystem.
   *
   * @param system the process system
   * @return the constructed graph
   */
  public static ProcessGraph buildGraph(ProcessSystem system) {
    if (system == null) {
      throw new IllegalArgumentException("system cannot be null");
    }

    ProcessGraph graph = new ProcessGraph();

    // Add all equipment as nodes
    List<ProcessEquipmentInterface> units = system.getUnitOperations();
    for (ProcessEquipmentInterface unit : units) {
      graph.addNode(unit);
    }

    // Build a map from stream objects to units that produce them (as outputs)
    Map<Object, ProcessEquipmentInterface> streamToProducer = new IdentityHashMap<>();

    // First pass: identify stream producers
    for (ProcessEquipmentInterface unit : units) {
      // Stream units produce themselves
      if (unit instanceof StreamInterface) {
        streamToProducer.put(unit, unit);
      }

      // TwoPort equipment produces outStream
      if (unit instanceof TwoPortInterface) {
        StreamInterface outStream = ((TwoPortInterface) unit).getOutletStream();
        if (outStream != null) {
          streamToProducer.put(outStream, unit);
        }
      }

      // Splitters produce split streams - use reflection since getSplitStream array not in
      // interface
      if (unit instanceof SplitterInterface) {
        collectSplitStreams(unit, streamToProducer);
      }

      // Mixers produce mixed stream
      if (unit instanceof MixerInterface) {
        MixerInterface mixer = (MixerInterface) unit;
        StreamInterface outStream = mixer.getOutletStream();
        if (outStream != null) {
          streamToProducer.put(outStream, unit);
        }
      }

      // HeatExchangers produce two output streams
      if (unit instanceof HeatExchanger) {
        HeatExchanger hx = (HeatExchanger) unit;
        for (int i = 0; i < 2; i++) {
          StreamInterface outStream = hx.getOutStream(i);
          if (outStream != null) {
            streamToProducer.put(outStream, unit);
          }
        }
      }

      // MultiStreamHeatExchangers produce multiple output streams
      if (unit instanceof MultiStreamHeatExchangerInterface) {
        MultiStreamHeatExchangerInterface mshx = (MultiStreamHeatExchangerInterface) unit;
        for (int i = 0; i < 20; i++) { // Reasonable upper limit
          try {
            StreamInterface outStream = mshx.getOutStream(i);
            if (outStream != null) {
              streamToProducer.put(outStream, unit);
            }
          } catch (Exception e) {
            break; // No more streams
          }
        }
      }

      // TurboExpanderCompressor produces expander and compressor outlet streams
      if (unit instanceof TurboExpanderCompressor) {
        TurboExpanderCompressor tec = (TurboExpanderCompressor) unit;
        StreamInterface expanderOut = tec.getExpanderOutletStream();
        if (expanderOut != null) {
          streamToProducer.put(expanderOut, unit);
        }
        StreamInterface compressorOut = tec.getCompressorOutletStream();
        if (compressorOut != null) {
          streamToProducer.put(compressorOut, unit);
        }
      }

      // Also check for common outlet method patterns
      collectProducedStreams(unit, streamToProducer);
    }

    // Second pass: identify stream consumers and create edges
    for (ProcessEquipmentInterface unit : units) {
      // Skip stream units - they don't consume other streams (typically)
      if (unit instanceof StreamInterface) {
        continue;
      }

      // TwoPort equipment consumes inStream
      if (unit instanceof TwoPortInterface) {
        StreamInterface inStream = ((TwoPortInterface) unit).getInletStream();
        if (inStream != null) {
          createEdgeFromProducer(graph, streamToProducer, inStream, unit);
        }
      }

      // Mixers consume multiple streams - use reflection since getStream(i) not in interface
      if (unit instanceof MixerInterface) {
        collectMixerInputStreamsAndCreateEdges(unit, graph, streamToProducer);
      }

      // HeatExchangers consume two input streams
      if (unit instanceof HeatExchanger) {
        HeatExchanger hx = (HeatExchanger) unit;
        for (int i = 0; i < 2; i++) {
          StreamInterface inStream = hx.getInStream(i);
          if (inStream != null) {
            createEdgeFromProducer(graph, streamToProducer, inStream, unit);
          }
        }
      }

      // MultiStreamHeatExchangers consume multiple input streams
      if (unit instanceof MultiStreamHeatExchangerInterface) {
        MultiStreamHeatExchangerInterface mshx = (MultiStreamHeatExchangerInterface) unit;
        for (int i = 0; i < 20; i++) { // Reasonable upper limit
          try {
            StreamInterface inStream = mshx.getInStream(i);
            if (inStream != null) {
              createEdgeFromProducer(graph, streamToProducer, inStream, unit);
            }
          } catch (Exception e) {
            break; // No more streams
          }
        }
      }

      // TurboExpanderCompressor consumes expander and compressor feed streams
      if (unit instanceof TurboExpanderCompressor) {
        TurboExpanderCompressor tec = (TurboExpanderCompressor) unit;
        StreamInterface expanderFeed = tec.getExpanderFeedStream();
        if (expanderFeed != null) {
          createEdgeFromProducer(graph, streamToProducer, expanderFeed, unit);
        }
        StreamInterface compressorFeed = tec.getCompressorFeedStream();
        if (compressorFeed != null) {
          createEdgeFromProducer(graph, streamToProducer, compressorFeed, unit);
        }
      }

      // Also check for inlet streams via reflection
      collectConsumedStreamsAndCreateEdges(unit, graph, streamToProducer);
    }

    return graph;
  }

  /**
   * Collects split streams from a Splitter via reflection. Uses the splitStream field or
   * getSplitStream(int) method.
   */
  private static void collectSplitStreams(ProcessEquipmentInterface unit,
      Map<Object, ProcessEquipmentInterface> streamToProducer) {
    // Try to access splitStream field directly
    try {
      Field splitStreamField = findField(unit.getClass(), "splitStream");
      if (splitStreamField != null) {
        splitStreamField.setAccessible(true);
        Object value = splitStreamField.get(unit);
        if (value != null && value.getClass().isArray()) {
          int length = Array.getLength(value);
          for (int i = 0; i < length; i++) {
            Object element = Array.get(value, i);
            if (element instanceof StreamInterface) {
              streamToProducer.put(element, unit);
            }
          }
        }
      }
    } catch (Exception e) {
      // Ignore and try getSplitStream(int) method
    }

    // Fallback: try getSplitStream(int) for indexed access
    SplitterInterface splitter = (SplitterInterface) unit;
    for (int i = 0; i < 20; i++) { // Reasonable upper limit
      try {
        StreamInterface stream = splitter.getSplitStream(i);
        if (stream != null) {
          streamToProducer.put(stream, unit);
        }
      } catch (Exception e) {
        break; // No more split streams
      }
    }
  }

  /**
   * Collects input streams from a Mixer and creates edges. Uses reflection since getStream(int) is
   * not in MixerInterface.
   */
  private static void collectMixerInputStreamsAndCreateEdges(ProcessEquipmentInterface unit,
      ProcessGraph graph, Map<Object, ProcessEquipmentInterface> streamToProducer) {
    // Try to access streams ArrayList directly
    try {
      Field streamsField = findField(unit.getClass(), "streams");
      if (streamsField != null) {
        streamsField.setAccessible(true);
        Object value = streamsField.get(unit);
        if (value instanceof java.util.List) {
          @SuppressWarnings("unchecked")
          java.util.List<StreamInterface> streams = (java.util.List<StreamInterface>) value;
          for (StreamInterface stream : streams) {
            if (stream != null) {
              createEdgeFromProducer(graph, streamToProducer, stream, unit);
            }
          }
          return;
        }
      }
    } catch (Exception e) {
      // Try method-based approach
    }

    // Fallback: try getStream(int) method via reflection
    try {
      Method getNumberMethod = unit.getClass().getMethod("getNumberOfInputStreams");
      Method getStreamMethod = unit.getClass().getMethod("getStream", int.class);
      int numStreams = (Integer) getNumberMethod.invoke(unit);
      for (int i = 0; i < numStreams; i++) {
        StreamInterface stream = (StreamInterface) getStreamMethod.invoke(unit, i);
        if (stream != null) {
          createEdgeFromProducer(graph, streamToProducer, stream, unit);
        }
      }
    } catch (Exception e) {
      // Ignore - methods may not exist
    }
  }

  /**
   * Finds a field in the class hierarchy.
   */
  private static Field findField(Class<?> clazz, String fieldName) {
    Class<?> type = clazz;
    while (type != null && type != Object.class) {
      try {
        return type.getDeclaredField(fieldName);
      } catch (NoSuchFieldException e) {
        type = type.getSuperclass();
      }
    }
    return null;
  }

  /**
   * Collects streams produced by a unit via common getter methods.
   */
  private static void collectProducedStreams(ProcessEquipmentInterface unit,
      Map<Object, ProcessEquipmentInterface> streamToProducer) {
    for (Method method : unit.getClass().getMethods()) {
      if (method.getDeclaringClass() == Object.class || method.getParameterCount() != 0) {
        continue;
      }

      String methodName = method.getName().toLowerCase();
      Class<?> returnType = method.getReturnType();

      // Check if this is an outlet method
      boolean isOutlet = methodName.contains("outlet") || methodName.contains("outstream")
          || methodName.contains("product") || methodName.contains("split")
          || methodName.contains("mixed") || methodName.contains("discharge")
          || methodName.contains("bottom") || methodName.contains("top")
          || methodName.contains("gasout") || methodName.contains("liquidout")
          || methodName.contains("vaporout");

      // Skip inlet methods
      boolean isInlet = methodName.contains("inlet") || methodName.contains("instream")
          || methodName.contains("feed");

      if (!isOutlet || isInlet) {
        continue;
      }

      if (StreamInterface.class.isAssignableFrom(returnType)) {
        Object result = invokeMethod(unit, method);
        if (result instanceof StreamInterface) {
          streamToProducer.putIfAbsent(result, unit);
        }
      } else if (returnType.isArray()
          && StreamInterface.class.isAssignableFrom(returnType.getComponentType())) {
        Object result = invokeMethod(unit, method);
        if (result != null) {
          int length = Array.getLength(result);
          for (int i = 0; i < length; i++) {
            Object element = Array.get(result, i);
            if (element instanceof StreamInterface) {
              streamToProducer.putIfAbsent(element, unit);
            }
          }
        }
      }
    }
  }

  /**
   * Collects streams consumed by a unit and creates edges.
   */
  private static void collectConsumedStreamsAndCreateEdges(ProcessEquipmentInterface unit,
      ProcessGraph graph, Map<Object, ProcessEquipmentInterface> streamToProducer) {
    Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());

    for (Method method : unit.getClass().getMethods()) {
      if (method.getDeclaringClass() == Object.class || method.getParameterCount() != 0) {
        continue;
      }

      String methodName = method.getName().toLowerCase();
      Class<?> returnType = method.getReturnType();

      // Check if this is an inlet method
      boolean isInlet = methodName.contains("inlet") || methodName.contains("instream")
          || methodName.contains("feed") || methodName.contains("input")
          || methodName.contains("suction");

      // Skip outlet methods for consumption detection
      boolean isOutlet = methodName.contains("outlet") || methodName.contains("outstream")
          || methodName.contains("product") || methodName.contains("split")
          || methodName.contains("mixed") || methodName.contains("discharge");

      if (!isInlet || isOutlet) {
        continue;
      }

      if (StreamInterface.class.isAssignableFrom(returnType)) {
        Object result = invokeMethod(unit, method);
        if (result instanceof StreamInterface && !visited.contains(result)) {
          visited.add(result);
          createEdgeFromProducer(graph, streamToProducer, result, unit);
        }
      }
    }

    // Also scan fields for streams named as inlets
    scanFieldsForInletStreams(unit, graph, streamToProducer, visited);
  }

  /**
   * Scans fields for inlet streams.
   */
  private static void scanFieldsForInletStreams(ProcessEquipmentInterface unit, ProcessGraph graph,
      Map<Object, ProcessEquipmentInterface> streamToProducer, Set<Object> visited) {
    Class<?> type = unit.getClass();
    while (type != null && type != Object.class) {
      for (Field field : type.getDeclaredFields()) {
        if (Modifier.isStatic(field.getModifiers())) {
          continue;
        }

        String fieldName = field.getName().toLowerCase();

        // Check if this looks like an inlet field
        boolean isInlet = fieldName.equals("instream") || fieldName.contains("inlet")
            || fieldName.contains("feed") || fieldName.contains("inletstream");

        // Skip outlet fields
        boolean isOutlet = fieldName.contains("outlet") || fieldName.equals("outstream")
            || fieldName.contains("product") || fieldName.contains("mixed");

        if (!isInlet || isOutlet) {
          continue;
        }

        // Handle StreamInterface fields
        if (StreamInterface.class.isAssignableFrom(field.getType())) {
          try {
            if (!field.isAccessible()) {
              field.setAccessible(true);
            }

            Object value = field.get(unit);
            if (value instanceof StreamInterface && !visited.contains(value)) {
              visited.add(value);
              createEdgeFromProducer(graph, streamToProducer, value, unit);
            }
          } catch (Exception e) {
            // Ignore inaccessible fields
          }
        }

        // Handle Mixer fields that contain inlet streams (like Separator.inletStreamMixer)
        if (MixerInterface.class.isAssignableFrom(field.getType())) {
          try {
            if (!field.isAccessible()) {
              field.setAccessible(true);
            }

            Object mixer = field.get(unit);
            if (mixer instanceof MixerInterface) {
              // Get streams from this internal mixer
              Field streamsField = findField(mixer.getClass(), "streams");
              if (streamsField != null) {
                streamsField.setAccessible(true);
                Object streamsValue = streamsField.get(mixer);
                if (streamsValue instanceof java.util.List) {
                  @SuppressWarnings("unchecked")
                  java.util.List<StreamInterface> streams =
                      (java.util.List<StreamInterface>) streamsValue;
                  for (StreamInterface inletStream : streams) {
                    if (inletStream != null && !visited.contains(inletStream)) {
                      visited.add(inletStream);
                      createEdgeFromProducer(graph, streamToProducer, inletStream, unit);
                    }
                  }
                }
              }
            }
          } catch (Exception e) {
            // Ignore inaccessible fields
          }
        }
      }
      type = type.getSuperclass();
    }
  }

  /**
   * Creates an edge from the producer of a stream to the consumer.
   */
  private static void createEdgeFromProducer(ProcessGraph graph,
      Map<Object, ProcessEquipmentInterface> streamToProducer, Object stream,
      ProcessEquipmentInterface consumer) {
    ProcessEquipmentInterface producer = streamToProducer.get(stream);
    if (producer != null && producer != consumer) {
      ProcessNode sourceNode = graph.getNode(producer);
      ProcessNode targetNode = graph.getNode(consumer);
      if (sourceNode != null && targetNode != null) {
        // Avoid duplicate edges
        boolean edgeExists = false;
        for (ProcessEdge edge : sourceNode.getOutgoingEdges()) {
          if (edge.getTarget() == targetNode) {
            edgeExists = true;
            break;
          }
        }
        if (!edgeExists) {
          StreamInterface streamIface =
              stream instanceof StreamInterface ? (StreamInterface) stream : null;
          graph.addEdge(sourceNode, targetNode, streamIface);
        }
      }
    }
  }

  /**
   * Safely invokes a method.
   */
  private static Object invokeMethod(ProcessEquipmentInterface unit, Method method) {
    try {
      return method.invoke(unit);
    } catch (Exception e) {
      return null;
    }
  }
}
