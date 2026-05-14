package neqsim.process.processmodel;

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * Exports a multi-area {@link ProcessModel} to Graphviz DOT diagrams.
 *
 * <p>
 * The exporter supports two complementary views: one common plant-wide DOT graph with one cluster
 * per {@link ProcessSystem} area, and one DOT graph per area for detailed plotting. The common
 * graph infers edges from shared stream object identity across areas, so live inter-area streams
 * become visible as cross-cluster edges.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class ProcessModelGraphvizExporter implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Process model to export. */
  private final ProcessModel model;

  /** Title used for the common DOT graph. */
  private String title = "Process Model";

  /**
   * Node descriptor used while constructing the common DOT graph.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static final class UnitNode {
    /** Name of the process area that owns the unit. */
    private final String areaName;

    /** Unit operation represented by the node. */
    private final ProcessEquipmentInterface unit;

    /** Unique DOT node id. */
    private final String nodeId;

    /**
     * Creates a unit-node descriptor.
     *
     * @param areaName process area name
     * @param unit unit operation represented by this node
     */
    private UnitNode(String areaName, ProcessEquipmentInterface unit) {
      this.areaName = areaName;
      this.unit = unit;
      this.nodeId = areaName + "::" + unit.getName();
    }
  }

  /**
   * Creates an exporter for a process model.
   *
   * @param model process model to export
   */
  public ProcessModelGraphvizExporter(ProcessModel model) {
    if (model == null) {
      throw new IllegalArgumentException("model must not be null");
    }
    this.model = model;
  }

  /**
   * Sets the title used in the common DOT graph.
   *
   * @param title graph title; ignored when {@code null}
   * @return this exporter for fluent configuration
   */
  public ProcessModelGraphvizExporter setTitle(String title) {
    if (title != null) {
      this.title = title;
    }
    return this;
  }

  /**
   * Returns the title used in the common DOT graph.
   *
   * @return graph title
   */
  public String getTitle() {
    return title;
  }

  /**
   * Generates a common plant-wide DOT graph with one cluster per process area.
   *
   * @return DOT-format string for the complete process model
   */
  public String toDot() {
    Map<String, List<UnitNode>> areaNodes = new LinkedHashMap<String, List<UnitNode>>();
    Map<String, Map<String, UnitNode>> areaNodeByName =
        new LinkedHashMap<String, Map<String, UnitNode>>();
    Map<StreamInterface, List<UnitNode>> producers =
        new IdentityHashMap<StreamInterface, List<UnitNode>>();
    Map<StreamInterface, List<UnitNode>> consumers =
        new IdentityHashMap<StreamInterface, List<UnitNode>>();

    collectNodesAndStreams(areaNodes, areaNodeByName, producers, consumers);

    StringBuilder builder = new StringBuilder();
    builder.append("digraph \"").append(escapeDot(title)).append("\" {\n");
    builder.append("  graph [compound=true, rankdir=LR, fontname=\"Arial\", labelloc=t, label=\"")
        .append(escapeDot(title)).append("\"];\n");
    builder
        .append("  node [shape=box, style=filled, fillcolor=\"#e8f0fe\", fontname=\"Arial\"];\n");
    builder.append("  edge [fontname=\"Arial\", fontsize=10];\n\n");

    appendAreaClusters(builder, areaNodes);
    appendStreamEdges(builder, producers, consumers);
    appendExplicitConnections(builder, areaNodeByName);

    builder.append("}\n");
    return builder.toString();
  }

  /**
   * Generates one DOT graph per process area.
   *
   * @return map from area name to DOT-format text
   */
  public Map<String, String> toAreaDots() {
    Map<String, String> dots = new LinkedHashMap<String, String>();
    for (String areaName : model.getProcessSystemNames()) {
      ProcessSystem process = model.get(areaName);
      if (process != null) {
        dots.put(areaName, process.toDOT());
      }
    }
    return dots;
  }

  /**
   * Writes the common plant-wide DOT graph to a file.
   *
   * @param path file path to write
   * @throws IOException if the parent directory cannot be created or the file cannot be written
   */
  public void exportDOT(Path path) throws IOException {
    if (path == null) {
      throw new IllegalArgumentException("path must not be null");
    }
    Path parent = path.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Files.write(path, toDot().getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Writes one DOT file per process area to the supplied directory.
   *
   * @param outputDirectory directory where area DOT files are written
   * @return map from area name to written file path
   * @throws IOException if the directory cannot be created or a file cannot be written
   */
  public Map<String, Path> exportAreaDOT(Path outputDirectory) throws IOException {
    if (outputDirectory == null) {
      throw new IllegalArgumentException("outputDirectory must not be null");
    }
    Files.createDirectories(outputDirectory);

    Map<String, Path> writtenFiles = new LinkedHashMap<String, Path>();
    Set<String> usedFileBases = new LinkedHashSet<String>();
    Map<String, String> dots = toAreaDots();
    for (Map.Entry<String, String> entry : dots.entrySet()) {
      String fileBase = uniqueFileBase(sanitizeFileBase(entry.getKey()), usedFileBases);
      Path areaPath = outputDirectory.resolve(fileBase + ".dot");
      Files.write(areaPath, entry.getValue().getBytes(StandardCharsets.UTF_8));
      writtenFiles.put(entry.getKey(), areaPath);
    }
    return writtenFiles;
  }

  /**
   * Collects area nodes and stream endpoints for the common DOT graph.
   *
   * @param areaNodes map to populate with nodes grouped by area
   * @param areaNodeByName map to populate with node lookup by area and unit name
   * @param producers map to populate with producer nodes by stream identity
   * @param consumers map to populate with consumer nodes by stream identity
   */
  private void collectNodesAndStreams(Map<String, List<UnitNode>> areaNodes,
      Map<String, Map<String, UnitNode>> areaNodeByName,
      Map<StreamInterface, List<UnitNode>> producers,
      Map<StreamInterface, List<UnitNode>> consumers) {
    for (String areaName : model.getProcessSystemNames()) {
      ProcessSystem process = model.get(areaName);
      if (process == null) {
        continue;
      }
      List<UnitNode> nodes = new ArrayList<UnitNode>();
      Map<String, UnitNode> nodesByName = new LinkedHashMap<String, UnitNode>();
      for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
        UnitNode node = new UnitNode(areaName, unit);
        nodes.add(node);
        nodesByName.put(unit.getName(), node);
      }
      areaNodes.put(areaName, nodes);
      areaNodeByName.put(areaName, nodesByName);

      for (UnitNode node : nodes) {
        collectStreamEndpoints(node, producers, consumers);
      }
    }
  }

  /**
   * Collects stream producer and consumer endpoints for one unit node.
   *
   * @param node unit node to inspect
   * @param producers stream producer map to populate
   * @param consumers stream consumer map to populate
   */
  private void collectStreamEndpoints(UnitNode node, Map<StreamInterface, List<UnitNode>> producers,
      Map<StreamInterface, List<UnitNode>> consumers) {
    if (node.unit instanceof StreamInterface) {
      addEndpoint(producers, (StreamInterface) node.unit, node);
    }
    for (StreamInterface stream : safeGetOutletStreams(node.unit)) {
      addEndpoint(producers, stream, node);
    }
    for (StreamInterface stream : safeGetInletStreams(node.unit)) {
      addEndpoint(consumers, stream, node);
    }
  }

  /**
   * Adds a unit node as an endpoint for a stream, preserving insertion order and uniqueness.
   *
   * @param endpoints endpoint map to update
   * @param stream stream identity key
   * @param node unit node endpoint
   */
  private void addEndpoint(Map<StreamInterface, List<UnitNode>> endpoints, StreamInterface stream,
      UnitNode node) {
    if (stream == null || node == null) {
      return;
    }
    List<UnitNode> nodes = endpoints.get(stream);
    if (nodes == null) {
      nodes = new ArrayList<UnitNode>();
      endpoints.put(stream, nodes);
    }
    if (!nodes.contains(node)) {
      nodes.add(node);
    }
  }

  /**
   * Safely returns outlet streams from a unit operation.
   *
   * @param unit unit operation to inspect
   * @return outlet streams, or an empty list if unavailable
   */
  private List<StreamInterface> safeGetOutletStreams(ProcessEquipmentInterface unit) {
    try {
      List<StreamInterface> streams = unit.getOutletStreams();
      return streams == null ? Collections.<StreamInterface>emptyList() : streams;
    } catch (Exception exception) {
      return Collections.emptyList();
    }
  }

  /**
   * Safely returns inlet streams from a unit operation.
   *
   * @param unit unit operation to inspect
   * @return inlet streams, or an empty list if unavailable
   */
  private List<StreamInterface> safeGetInletStreams(ProcessEquipmentInterface unit) {
    try {
      List<StreamInterface> streams = unit.getInletStreams();
      return streams == null ? Collections.<StreamInterface>emptyList() : streams;
    } catch (Exception exception) {
      return Collections.emptyList();
    }
  }

  /**
   * Appends clusters for all process areas.
   *
   * @param builder DOT builder to append to
   * @param areaNodes nodes grouped by area name
   */
  private void appendAreaClusters(StringBuilder builder, Map<String, List<UnitNode>> areaNodes) {
    int areaIndex = 0;
    for (Map.Entry<String, List<UnitNode>> entry : areaNodes.entrySet()) {
      builder.append("  subgraph cluster_").append(areaIndex).append(" {\n");
      builder.append("    label=\"").append(escapeDot(entry.getKey())).append("\";\n");
      builder.append("    style=\"rounded\";\n");
      builder.append("    color=\"#9aa5b1\";\n");
      for (UnitNode node : entry.getValue()) {
        builder.append("    \"").append(escapeDot(node.nodeId)).append("\" [label=\"")
            .append(escapeDot(node.unit.getName())).append("\", shape=")
            .append(getShapeForEquipment(node.unit)).append("];\n");
      }
      builder.append("  }\n\n");
      areaIndex++;
    }
  }

  /**
   * Appends stream-derived edges to the common DOT graph.
   *
   * @param builder DOT builder to append to
   * @param producers producer nodes by stream identity
   * @param consumers consumer nodes by stream identity
   */
  private void appendStreamEdges(StringBuilder builder,
      Map<StreamInterface, List<UnitNode>> producers,
      Map<StreamInterface, List<UnitNode>> consumers) {
    Set<String> edgeLines = new LinkedHashSet<String>();
    for (Map.Entry<StreamInterface, List<UnitNode>> entry : producers.entrySet()) {
      StreamInterface stream = entry.getKey();
      List<UnitNode> sourceNodes = selectEffectiveSources(entry.getValue());
      List<UnitNode> sinkNodes = consumers.get(stream);
      if (sinkNodes == null || sinkNodes.isEmpty()) {
        continue;
      }
      for (UnitNode source : sourceNodes) {
        for (UnitNode sink : sinkNodes) {
          addStreamEdge(edgeLines, source, sink, stream);
        }
      }
    }
    appendEdgeLines(builder, edgeLines);
  }

  /**
   * Selects source nodes for a stream, preferring equipment producers over stream-unit producers.
   *
   * @param sourceNodes all source nodes registered for a stream
   * @return effective source nodes to use in graph edges
   */
  private List<UnitNode> selectEffectiveSources(List<UnitNode> sourceNodes) {
    List<UnitNode> equipmentSources = new ArrayList<UnitNode>();
    for (UnitNode source : sourceNodes) {
      if (!(source.unit instanceof StreamInterface)) {
        equipmentSources.add(source);
      }
    }
    return equipmentSources.isEmpty() ? sourceNodes : equipmentSources;
  }

  /**
   * Adds one stream-derived edge line if the endpoints are distinct.
   *
   * @param edgeLines edge-line set to update
   * @param source source node
   * @param sink sink node
   * @param stream stream carried by the edge
   */
  private void addStreamEdge(Set<String> edgeLines, UnitNode source, UnitNode sink,
      StreamInterface stream) {
    if (source == null || sink == null || source == sink) {
      return;
    }
    List<String> attributes = new ArrayList<String>();
    String streamName = stream.getName();
    if (streamName != null && !streamName.isEmpty()) {
      attributes.add("label=\"" + escapeDot(streamName) + "\"");
    }
    if (!source.areaName.equals(sink.areaName)) {
      attributes.add("color=\"#455a64\"");
      attributes.add("penwidth=2.0");
    }
    edgeLines.add(buildEdgeLine(source.nodeId, sink.nodeId, attributes));
  }

  /**
   * Appends explicit connection metadata as graph edges.
   *
   * @param builder DOT builder to append to
   * @param areaNodeByName node lookup by area and unit name
   */
  private void appendExplicitConnections(StringBuilder builder,
      Map<String, Map<String, UnitNode>> areaNodeByName) {
    Set<String> edgeLines = new LinkedHashSet<String>();
    for (String areaName : model.getProcessSystemNames()) {
      ProcessSystem process = model.get(areaName);
      Map<String, UnitNode> nodesByName = areaNodeByName.get(areaName);
      if (process == null || nodesByName == null) {
        continue;
      }
      for (ProcessConnection connection : process.getConnections()) {
        UnitNode source = nodesByName.get(connection.getSourceEquipment());
        UnitNode target = nodesByName.get(connection.getTargetEquipment());
        addExplicitConnectionEdge(edgeLines, source, target, connection);
      }
    }
    appendEdgeLines(builder, edgeLines);
  }

  /**
   * Adds one explicit connection edge if both endpoints are present.
   *
   * @param edgeLines edge-line set to update
   * @param source source node
   * @param target target node
   * @param connection explicit process connection
   */
  private void addExplicitConnectionEdge(Set<String> edgeLines, UnitNode source, UnitNode target,
      ProcessConnection connection) {
    if (source == null || target == null || source == target || connection == null) {
      return;
    }
    List<String> attributes = new ArrayList<String>();
    attributes.add("label=\"" + escapeDot(connection.getSourcePort()) + "\"");
    if (connection.getType() == ProcessConnection.ConnectionType.SIGNAL) {
      attributes.add("style=dashed");
      attributes.add("color=blue");
    } else if (connection.getType() == ProcessConnection.ConnectionType.ENERGY) {
      attributes.add("style=dotted");
      attributes.add("color=red");
    }
    edgeLines.add(buildEdgeLine(source.nodeId, target.nodeId, attributes));
  }

  /**
   * Appends prepared edge lines to a DOT graph builder.
   *
   * @param builder DOT builder to append to
   * @param edgeLines prepared edge lines
   */
  private void appendEdgeLines(StringBuilder builder, Set<String> edgeLines) {
    if (edgeLines.isEmpty()) {
      return;
    }
    for (String edgeLine : edgeLines) {
      builder.append(edgeLine).append("\n");
    }
    builder.append("\n");
  }

  /**
   * Builds a DOT edge line from endpoint ids and attributes.
   *
   * @param sourceId source node id
   * @param targetId target node id
   * @param attributes DOT attributes to include
   * @return formatted DOT edge line
   */
  private String buildEdgeLine(String sourceId, String targetId, List<String> attributes) {
    StringBuilder builder = new StringBuilder();
    builder.append("  \"").append(escapeDot(sourceId)).append("\" -> \"")
        .append(escapeDot(targetId)).append("\"");
    if (attributes != null && !attributes.isEmpty()) {
      builder.append(" [").append(joinAttributes(attributes)).append("]");
    }
    builder.append(";");
    return builder.toString();
  }

  /**
   * Joins DOT attributes with comma separators.
   *
   * @param attributes attribute strings
   * @return joined attribute text
   */
  private String joinAttributes(List<String> attributes) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < attributes.size(); i++) {
      if (i > 0) {
        builder.append(", ");
      }
      builder.append(attributes.get(i));
    }
    return builder.toString();
  }

  /**
   * Returns a simple Graphviz shape for an equipment type.
   *
   * @param equipment equipment to classify
   * @return Graphviz shape name
   */
  private String getShapeForEquipment(ProcessEquipmentInterface equipment) {
    String className = equipment.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);
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
    } else if (equipment instanceof StreamInterface) {
      return "plaintext";
    }
    return "box";
  }

  /**
   * Escapes text for Graphviz quoted strings.
   *
   * @param value text to escape
   * @return escaped text
   */
  private String escapeDot(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  /**
   * Sanitizes an area name for use as a file base name.
   *
   * @param value area name
   * @return safe file base name
   */
  private String sanitizeFileBase(String value) {
    String candidate = value == null ? "area" : value.trim();
    candidate = candidate.replaceAll("[^A-Za-z0-9._-]+", "_");
    candidate = candidate.replaceAll("^[._-]+", "").replaceAll("[._-]+$", "");
    if (candidate.isEmpty()) {
      candidate = "area";
    }
    return candidate;
  }

  /**
   * Makes a file base unique within an export operation.
   *
   * @param preferred preferred base name
   * @param usedFileBases file bases already used
   * @return unique file base name
   */
  private String uniqueFileBase(String preferred, Set<String> usedFileBases) {
    String candidate = preferred;
    int suffix = 2;
    while (usedFileBases.contains(candidate)) {
      candidate = preferred + "_" + suffix;
      suffix++;
    }
    usedFileBases.add(candidate);
    return candidate;
  }
}
