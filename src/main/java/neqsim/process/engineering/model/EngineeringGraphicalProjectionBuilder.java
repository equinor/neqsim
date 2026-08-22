package neqsim.process.engineering.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import neqsim.process.engineering.model.EngineeringDiagramConventionRegister.EvidenceState;
import neqsim.process.engineering.model.EngineeringDiagramConventionRegister.SymbolConvention;
import neqsim.process.engineering.model.EngineeringDiagramConventionRegister.SymbolShape;
import neqsim.process.engineering.model.EngineeringGraphicalProjection.Diagnostic;
import neqsim.process.engineering.model.EngineeringGraphicalProjection.Point;
import neqsim.process.engineering.model.EngineeringGraphicalProjection.Primitive;
import neqsim.process.engineering.model.EngineeringGraphicalProjection.Severity;
import neqsim.process.engineering.model.EngineeringGraphicalProjection.VerificationStatus;

/** Builds deterministic generic drawing primitives from the canonical engineering graph. */
public final class EngineeringGraphicalProjectionBuilder {
  private static final String FALLBACK_STROKE = "#374151";
  private static final String FALLBACK_FILL = "#ffffff";

  private EngineeringGraphicalProjectionBuilder() {
  }

  /**
   * Builds one exchange-neutral graphical projection.
   *
   * <p>
   * Missing symbol conventions are represented by explicit generic rectangles and structured
   * diagnostics. Reviewed project conventions control only generic shape and colour; they do not
   * create a standards-profile symbol binding or approve a P&amp;ID.
   * </p>
   *
   * @param graph canonical semantic graph
   * @param conventions project convention register; may be empty
   * @param sourceReference controlled source or document reference
   * @param verificationStatus proposal or reviewed-input status
   * @return deterministic immutable graphical projection
   */
  public static EngineeringGraphicalProjection build(EngineeringGraph graph,
      EngineeringDiagramConventionRegister conventions, String sourceReference,
      VerificationStatus verificationStatus) {
    if (graph == null) {
      throw new IllegalArgumentException("graph must not be null");
    }
    if (conventions == null) {
      throw new IllegalArgumentException("conventions must not be null");
    }
    if (verificationStatus == null) {
      throw new IllegalArgumentException("verificationStatus must not be null");
    }
    Map<String, Object> layout = EngineeringDiagramLayout.build(graph);
    List<Primitive> primitives = new ArrayList<Primitive>();
    List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
    Set<EngineeringNode.Kind> fallbackKinds = EnumSet.noneOf(EngineeringNode.Kind.class);
    Set<EngineeringNode.Kind> proposedKinds = EnumSet.noneOf(EngineeringNode.Kind.class);

    for (Map<String, Object> placement : maps(layout.get("placements"))) {
      String nodeId = text(placement.get("nodeId"));
      EngineeringNode node = graph.getNode(nodeId);
      if (node == null) {
        diagnostics.add(new Diagnostic(Severity.ERROR, "GRAPHICAL_PROJECTION_NODE_MISSING",
            "Layout placement references a node absent from the canonical engineering graph",
            nodeId));
        continue;
      }
      double x = number(placement.get("x"));
      double y = number(placement.get("y"));
      double width = number(placement.get("width"));
      double height = number(placement.get("height"));
      SymbolConvention convention = conventions.getSymbolConvention(node.getKind());
      SymbolShape shape = convention == null ? SymbolShape.RECTANGLE : convention.getShape();
      String stroke = convention == null ? FALLBACK_STROKE : convention.getStrokeColor();
      String fill = convention == null ? FALLBACK_FILL : convention.getFillColor();
      if (convention == null && fallbackKinds.add(node.getKind())) {
        diagnostics.add(new Diagnostic(Severity.WARNING,
            "GRAPHICAL_PROJECTION_GENERIC_SYMBOL_FALLBACK",
            "No project convention is registered; generic rectangles are retained without a standards-symbol claim",
            node.getKind().name()));
      } else if (convention != null && convention.getEvidenceState() == EvidenceState.PROPOSED
          && proposedKinds.add(node.getKind())) {
        diagnostics.add(new Diagnostic(Severity.INFO,
            "GRAPHICAL_PROJECTION_PROPOSED_CONVENTION",
            "Project convention is proposed and still requires accountable review",
            node.getKind().name()));
      }
      primitives.add(shape(node, shape, x, y, width, height, stroke, fill));
      primitives.add(Primitive.text(nodeId + ":label", nodeId, node.getExternalKey(), x, y,
          3.0, node.getLabel(), "#111827", "middle"));
    }

    List<Map<String, Object>> routes = maps(layout.get("routes"));
    Set<String> routedEdgeIds = new TreeSet<String>();
    Collections.sort(routes, new Comparator<Map<String, Object>>() {
      @Override
      public int compare(Map<String, Object> left, Map<String, Object> right) {
        return text(left.get("edgeId")).compareTo(text(right.get("edgeId")));
      }
    });
    for (Map<String, Object> route : routes) {
      String edgeId = text(route.get("edgeId"));
      EngineeringEdge edge = graph.getEdges().get(edgeId);
      if (edge == null) {
        diagnostics.add(new Diagnostic(Severity.ERROR, "GRAPHICAL_PROJECTION_EDGE_MISSING",
            "Layout route references an edge absent from the canonical engineering graph",
            edgeId));
        continue;
      }
      routedEdgeIds.add(edgeId);
      List<Point> points = new ArrayList<Point>();
      for (Map<String, Object> point : maps(route.get("points"))) {
        points.add(new Point(number(point.get("x")), number(point.get("y"))));
      }
      primitives.add(Primitive.polyline(edgeId + ":route", edgeId, edgeId, points,
          routeColor(edge.getKind()), 0.8, routeDash(edge.getKind()), false));
    }
    for (EngineeringEdge edge : graph.getEdges().values()) {
      if (isGraphicalRoute(edge.getKind()) && !routedEdgeIds.contains(edge.getId())) {
        diagnostics.add(new Diagnostic(Severity.WARNING,
            "GRAPHICAL_PROJECTION_ROUTE_ENDPOINT_NOT_PLACED",
            "Graphical relationship is retained semantically but has no route because an endpoint is not drawable",
            edge.getId()));
      }
    }
    if (primitives.isEmpty()) {
      diagnostics.add(new Diagnostic(Severity.ERROR, "GRAPHICAL_PROJECTION_EMPTY",
          "Canonical engineering graph contains no drawable nodes or routes", graph.getProjectId()));
    }

    return new EngineeringGraphicalProjection(graph.getProjectId(), graph.getRevision(),
        text(graph.toMap().get("fingerprint")), sourceReference, verificationStatus, primitives,
        diagnostics);
  }

  private static Primitive shape(EngineeringNode node, SymbolShape shape, double x, double y,
      double width, double height, String stroke, String fill) {
    String id = node.getId() + ":shape";
    if (shape == SymbolShape.DIAMOND) {
      List<Point> points = new ArrayList<Point>();
      points.add(new Point(x, y - height / 2.0));
      points.add(new Point(x + width / 2.0, y));
      points.add(new Point(x, y + height / 2.0));
      points.add(new Point(x - width / 2.0, y));
      return Primitive.polygon(id, node.getId(), node.getExternalKey(), points, stroke, fill, 0.8);
    }
    if (shape == SymbolShape.HEXAGON) {
      double shoulder = width * 0.2;
      List<Point> points = new ArrayList<Point>();
      points.add(new Point(x - width / 2.0 + shoulder, y - height / 2.0));
      points.add(new Point(x + width / 2.0 - shoulder, y - height / 2.0));
      points.add(new Point(x + width / 2.0, y));
      points.add(new Point(x + width / 2.0 - shoulder, y + height / 2.0));
      points.add(new Point(x - width / 2.0 + shoulder, y + height / 2.0));
      points.add(new Point(x - width / 2.0, y));
      return Primitive.polygon(id, node.getId(), node.getExternalKey(), points, stroke, fill, 0.8);
    }
    return Primitive.rectangle(id, node.getId(), node.getExternalKey(), x - width / 2.0,
        y - height / 2.0, width, height, stroke, fill, 0.8);
  }

  private static String routeColor(EngineeringEdge.Kind kind) {
    if (kind == EngineeringEdge.Kind.ENERGY_FLOW) {
      return "#d97706";
    }
    if (kind == EngineeringEdge.Kind.SIGNAL_FLOW || kind == EngineeringEdge.Kind.MEASURES) {
      return "#7c3aed";
    }
    if (kind == EngineeringEdge.Kind.PROCESS_FLOW || kind == EngineeringEdge.Kind.CONNECTS_TO
        || kind == EngineeringEdge.Kind.PART_OF_LINE) {
      return "#2563eb";
    }
    return "#64748b";
  }

  private static String routeDash(EngineeringEdge.Kind kind) {
    return kind == EngineeringEdge.Kind.SIGNAL_FLOW || kind == EngineeringEdge.Kind.MEASURES
        ? "4 2"
        : "";
  }

  private static boolean isGraphicalRoute(EngineeringEdge.Kind kind) {
    return kind == EngineeringEdge.Kind.PROCESS_FLOW || kind == EngineeringEdge.Kind.SIGNAL_FLOW
        || kind == EngineeringEdge.Kind.ENERGY_FLOW || kind == EngineeringEdge.Kind.CONNECTS_TO
        || kind == EngineeringEdge.Kind.HAS_PORT || kind == EngineeringEdge.Kind.PART_OF_LINE
        || kind == EngineeringEdge.Kind.MEASURES;
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> maps(Object value) {
    if (!(value instanceof List<?>)) {
      throw new IllegalArgumentException("Layout collection is missing");
    }
    List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
    for (Object item : (List<?>) value) {
      if (!(item instanceof Map<?, ?>)) {
        throw new IllegalArgumentException("Layout collection contains a non-map item");
      }
      result.add((Map<String, Object>) item);
    }
    return result;
  }

  private static String text(Object value) {
    if (value == null || value.toString().trim().isEmpty()) {
      throw new IllegalArgumentException("Layout text value is missing");
    }
    return value.toString().trim();
  }

  private static double number(Object value) {
    if (!(value instanceof Number)) {
      throw new IllegalArgumentException("Layout numeric value is missing");
    }
    return ((Number) value).doubleValue();
  }
}
