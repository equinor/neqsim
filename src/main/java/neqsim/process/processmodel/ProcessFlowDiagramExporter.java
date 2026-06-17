package neqsim.process.processmodel;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * Exports a {@link ProcessSystem} topology to Graphviz DOT format for PFD generation.
 *
 * <p>
 * The exporter walks all unit operations, queries their inlet and outlet streams via
 * {@link ProcessEquipmentInterface#getInletStreams()} and
 * {@link ProcessEquipmentInterface#getOutletStreams()}, and infers edges wherever two equipment
 * units share a stream object. Explicit {@link ProcessConnection} metadata is also rendered as
 * edges.
 * </p>
 *
 * <p>
 * Usage:
 * </p>
 *
 * <pre>
 * ProcessSystem process = ...;
 * ProcessFlowDiagramExporter exporter = new ProcessFlowDiagramExporter(process);
 * String dot = exporter.toDot();
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class ProcessFlowDiagramExporter implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** The process system to export. */
  private final ProcessSystem processSystem;

  /** Graph title. */
  private String title = "Process Flow Diagram";

  /**
   * Creates an exporter for the given process system.
   *
   * @param processSystem the process system to export
   */
  public ProcessFlowDiagramExporter(ProcessSystem processSystem) {
    if (processSystem == null) {
      throw new IllegalArgumentException("processSystem must not be null");
    }
    this.processSystem = processSystem;
  }

  /**
   * Sets the title shown in the DOT graph.
   *
   * @param title graph title string
   */
  public void setTitle(String title) {
    this.title = title;
  }

  /**
   * Generates the Graphviz DOT representation of the process topology.
   *
   * <p>
   * Equipment becomes graph nodes, streams become edges. The method first discovers topology from
   * stream sharing, then adds explicit connections from {@link ProcessSystem#getConnections()}.
   * </p>
   *
   * @return DOT-format string
   */
  public String toDot() {
    List<ProcessEquipmentInterface> units = processSystem.getUnitOperations();

    // Map each stream identity to its producing equipment
    Map<Integer, String> streamProducers = new LinkedHashMap<Integer, String>();
    // Map each stream identity to its consuming equipment
    Map<Integer, String> streamConsumers = new LinkedHashMap<Integer, String>();

    for (ProcessEquipmentInterface equip : units) {
      for (StreamInterface s : equip.getOutletStreams()) {
        streamProducers.put(System.identityHashCode(s), equip.getName());
      }
      for (StreamInterface s : equip.getInletStreams()) {
        streamConsumers.put(System.identityHashCode(s), equip.getName());
      }
    }

    StringBuilder sb = new StringBuilder();
    sb.append("digraph \"").append(escapeQuotes(title)).append("\" {\n");
    sb.append("  rankdir=LR;\n");
    sb.append("  node [shape=box, style=filled, fillcolor=\"#e8f0fe\", fontname=Arial];\n");
    sb.append("  edge [fontname=Arial, fontsize=10];\n");
    sb.append("\n");

    // Emit nodes
    for (ProcessEquipmentInterface equip : units) {
      String nodeName = sanitizeId(equip.getName());
      String nodeShape = getShapeForEquipment(equip);
      sb.append("  ").append(nodeName).append(" [label=\"").append(escapeQuotes(equip.getName()))
          .append("\", shape=").append(nodeShape).append("];\n");
    }
    sb.append("\n");

    // Emit edges from shared streams
    Map<String, Boolean> edgeSet = new LinkedHashMap<String, Boolean>();
    for (Map.Entry<Integer, String> entry : streamProducers.entrySet()) {
      int streamId = entry.getKey();
      String producer = entry.getValue();
      String consumer = streamConsumers.get(streamId);
      if (consumer != null && !producer.equals(consumer)) {
        String edgeKey = producer + " -> " + consumer;
        if (!edgeSet.containsKey(edgeKey)) {
          edgeSet.put(edgeKey, Boolean.TRUE);
          sb.append("  ").append(sanitizeId(producer)).append(" -> ").append(sanitizeId(consumer))
              .append(";\n");
        }
      }
    }

    // Add explicit connections
    for (ProcessConnection conn : processSystem.getConnections()) {
      String edgeKey = conn.getSourceEquipment() + " -> " + conn.getTargetEquipment();
      if (!edgeSet.containsKey(edgeKey)) {
        edgeSet.put(edgeKey, Boolean.TRUE);
        String style =
            conn.getType() == ProcessConnection.ConnectionType.SIGNAL ? ", style=dashed, color=blue"
                : conn.getType() == ProcessConnection.ConnectionType.ENERGY
                    ? ", style=dotted, color=red"
                    : "";
        sb.append("  ").append(sanitizeId(conn.getSourceEquipment())).append(" -> ")
            .append(sanitizeId(conn.getTargetEquipment())).append(" [label=\"")
            .append(escapeQuotes(conn.getSourcePort())).append("\"").append(style).append("];\n");
      }
    }

    sb.append("}\n");
    return sb.toString();
  }

  /**
   * Returns an appropriate Graphviz shape for the equipment class.
   *
   * @param equip the equipment
   * @return Graphviz shape string
   */
  private String getShapeForEquipment(ProcessEquipmentInterface equip) {
    String className = equip.getClass().getSimpleName().toLowerCase();
    if (className.contains("separator") || className.contains("scrubber")) {
      return "ellipse";
    } else if (className.contains("compressor") || className.contains("pump")
        || className.contains("expander")) {
      return "trapezium";
    } else if (className.contains("valve") || className.contains("throttle")) {
      return "diamond";
    } else if (className.contains("heater") || className.contains("cooler")
        || className.contains("heatexchanger")) {
      return "parallelogram";
    } else if (className.contains("mixer") || className.contains("splitter")) {
      return "triangle";
    } else if (className.contains("column") || className.contains("distillation")) {
      return "hexagon";
    } else if (className.contains("stream")) {
      return "plaintext";
    }
    return "box";
  }

  /**
   * Sanitizes a name for use as a Graphviz node ID by replacing non-alphanumeric characters with
   * underscores.
   *
   * @param name the equipment name
   * @return sanitized node ID
   */
  private String sanitizeId(String name) {
    return "\"" + escapeQuotes(name) + "\"";
  }

  /**
   * Escapes double quotes in strings for DOT format.
   *
   * @param s input string
   * @return escaped string
   */
  private String escapeQuotes(String s) {
    if (s == null) {
      return "";
    }
    return s.replace("\"", "\\\"");
  }
}
