package neqsim.process.engineering.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/** Deterministic constraint-based placement and orthogonal routing derived from the canonical engineering graph. */
public final class EngineeringDiagramLayout implements Serializable {
  private static final long serialVersionUID = 1000L;

  private EngineeringDiagramLayout() {
  }

  /** Builds stable coordinates without making the exchange document the engineering database. */
  public static Map<String, Object> build(EngineeringGraph graph) {
    if (graph == null) {
      throw new IllegalArgumentException("graph must not be null");
    }
    List<EngineeringNode> visualNodes = new ArrayList<EngineeringNode>();
    for (EngineeringNode node : graph.getNodes().values()) {
      if (isVisual(node.getKind())) {
        visualNodes.add(node);
      }
    }
    Collections.sort(visualNodes, new Comparator<EngineeringNode>() {
      @Override
      public int compare(EngineeringNode first, EngineeringNode second) {
        int kind = first.getKind().name().compareTo(second.getKind().name());
        return kind == 0 ? first.getExternalKey().compareTo(second.getExternalKey()) : kind;
      }
    });
    Map<String, Integer> rank = ranks(graph, visualNodes);
    Map<Integer, Integer> rowByRank = new HashMap<Integer, Integer>();
    Map<String, double[]> coordinates = new LinkedHashMap<String, double[]>();
    List<Map<String, Object>> placements = new ArrayList<Map<String, Object>>();
    for (EngineeringNode node : visualNodes) {
      int layer = rank.get(node.getId()).intValue();
      Integer previousRows = rowByRank.get(Integer.valueOf(layer));
      int row = previousRows == null ? 0 : previousRows.intValue();
      rowByRank.put(Integer.valueOf(layer), Integer.valueOf(row + 1));
      double x = 30.0 + 55.0 * layer;
      double y = 35.0 + 32.0 * row;
      coordinates.put(node.getId(), new double[] { x, y });
      Map<String, Object> placement = new LinkedHashMap<String, Object>();
      placement.put("nodeId", node.getId());
      placement.put("externalKey", node.getExternalKey());
      placement.put("kind", node.getKind().name());
      placement.put("layer", Integer.valueOf(layer));
      placement.put("x", Double.valueOf(x));
      placement.put("y", Double.valueOf(y));
      placement.put("width", Double.valueOf(width(node.getKind())));
      placement.put("height", Double.valueOf(14.0));
      placements.add(placement);
    }
    List<Map<String, Object>> routes = new ArrayList<Map<String, Object>>();
    for (EngineeringEdge edge : graph.getEdges().values()) {
      double[] source = coordinates.get(edge.getSourceId());
      double[] target = coordinates.get(edge.getTargetId());
      if (source == null || target == null || !isRouted(edge.getKind())) {
        continue;
      }
      double midX = 0.5 * (source[0] + target[0]);
      Map<String, Object> route = new LinkedHashMap<String, Object>();
      route.put("edgeId", edge.getId());
      route.put("kind", edge.getKind().name());
      route.put("sourceId", edge.getSourceId());
      route.put("targetId", edge.getTargetId());
      List<Map<String, Double>> points = new ArrayList<Map<String, Double>>();
      points.add(point(source[0], source[1]));
      points.add(point(midX, source[1]));
      points.add(point(midX, target[1]));
      points.add(point(target[0], target[1]));
      route.put("points", points);
      routes.add(route);
    }
    Map<String, Object> constraints = new LinkedHashMap<String, Object>();
    constraints.put("flowDirection", "LEFT_TO_RIGHT");
    constraints.put("minimumHorizontalSpacing", Double.valueOf(55.0));
    constraints.put("minimumVerticalSpacing", Double.valueOf(32.0));
    constraints.put("routing", "ORTHOGONAL");
    constraints.put("deterministicOrder", "KIND_THEN_EXTERNAL_KEY");
    constraints.put("cycleHandling", "CYCLIC_NODES_RETAIN_EARLIEST_STABLE_LAYER");
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("schemaVersion", "neqsim_engineering_diagram_layout.v1");
    result.put("projectId", graph.getProjectId());
    result.put("revision", graph.getRevision());
    result.put("graphFingerprint", graph.toMap().get("fingerprint"));
    result.put("representations", java.util.Arrays.asList("PFD", "P&ID"));
    result.put("constraints", constraints);
    result.put("placements", placements);
    result.put("routes", routes);
    result.put("engineeringApprovalRequired", Boolean.TRUE);
    return result;
  }

  private static Map<String, Integer> ranks(EngineeringGraph graph, List<EngineeringNode> nodes) {
    Map<String, Integer> result = new LinkedHashMap<String, Integer>();
    Map<String, Integer> indegree = new LinkedHashMap<String, Integer>();
    Map<String, List<String>> targets = new LinkedHashMap<String, List<String>>();
    for (EngineeringNode node : nodes) {
      result.put(node.getId(), Integer.valueOf(0));
      indegree.put(node.getId(), Integer.valueOf(0));
      targets.put(node.getId(), new ArrayList<String>());
    }
    for (EngineeringEdge edge : graph.getEdges().values()) {
      if (!isRankFlow(edge.getKind()) || !result.containsKey(edge.getSourceId())
          || !result.containsKey(edge.getTargetId())) {
        continue;
      }
      targets.get(edge.getSourceId()).add(edge.getTargetId());
      indegree.put(edge.getTargetId(), Integer.valueOf(indegree.get(edge.getTargetId()).intValue() + 1));
    }
    PriorityQueue<String> ready = new PriorityQueue<String>();
    for (List<String> adjacent : targets.values()) {
      Collections.sort(adjacent);
    }
    for (Map.Entry<String, Integer> item : indegree.entrySet()) {
      if (item.getValue().intValue() == 0) {
        ready.add(item.getKey());
      }
    }
    while (!ready.isEmpty()) {
      String sourceId = ready.remove();
      for (String targetId : targets.get(sourceId)) {
        result.put(targetId,
            Integer.valueOf(Math.max(result.get(targetId).intValue(), result.get(sourceId).intValue() + 1)));
        int remaining = indegree.get(targetId).intValue() - 1;
        indegree.put(targetId, Integer.valueOf(remaining));
        if (remaining == 0) {
          ready.add(targetId);
        }
      }
    }
    return result;
  }

  private static boolean isRankFlow(EngineeringEdge.Kind kind) {
    return kind == EngineeringEdge.Kind.PROCESS_FLOW || kind == EngineeringEdge.Kind.SIGNAL_FLOW
        || kind == EngineeringEdge.Kind.ENERGY_FLOW;
  }

  private static boolean isVisual(EngineeringNode.Kind kind) {
    return kind == EngineeringNode.Kind.EQUIPMENT || kind == EngineeringNode.Kind.LINE
        || kind == EngineeringNode.Kind.INSTRUMENT || kind == EngineeringNode.Kind.BOUNDARY
        || kind == EngineeringNode.Kind.PIPE_SEGMENT || kind == EngineeringNode.Kind.SIGNAL_CONNECTION
        || kind == EngineeringNode.Kind.NOZZLE || kind == EngineeringNode.Kind.PORT;
  }

  private static boolean isFlow(EngineeringEdge.Kind kind) {
    return kind == EngineeringEdge.Kind.PROCESS_FLOW || kind == EngineeringEdge.Kind.SIGNAL_FLOW
        || kind == EngineeringEdge.Kind.ENERGY_FLOW || kind == EngineeringEdge.Kind.CONNECTS_TO;
  }

  private static boolean isRouted(EngineeringEdge.Kind kind) {
    return isFlow(kind) || kind == EngineeringEdge.Kind.HAS_PORT || kind == EngineeringEdge.Kind.PART_OF_LINE
        || kind == EngineeringEdge.Kind.MEASURES;
  }

  private static double width(EngineeringNode.Kind kind) {
    return kind == EngineeringNode.Kind.EQUIPMENT ? 28.0 : kind == EngineeringNode.Kind.INSTRUMENT ? 12.0 : 18.0;
  }

  private static Map<String, Double> point(double x, double y) {
    Map<String, Double> result = new LinkedHashMap<String, Double>();
    result.put("x", Double.valueOf(x));
    result.put("y", Double.valueOf(y));
    return result;
  }
}
