package neqsim.process.engineering.validation;

import neqsim.process.engineering.model.EngineeringEdge;
import neqsim.process.engineering.model.EngineeringGraph;
import neqsim.process.engineering.model.EngineeringNode;

/** Validates canonical ports, connection segments, flow direction and controlled boundary topology. */
public final class EngineeringTopologyValidator {
  private static final String ARTIFACT = "engineering-model.json";

  private EngineeringTopologyValidator() {
  }

  /** Validates the physical and signal topology embedded in an engineering graph. */
  public static EngineeringPackageValidationReport validate(EngineeringGraph graph) {
    EngineeringPackageValidationReport report = new EngineeringPackageValidationReport();
    if (graph == null) {
      report.addError("ENG-TOPOLOGY-001", ARTIFACT, "", "Engineering graph is missing");
      return report;
    }
    for (EngineeringNode node : graph.getNodes().values()) {
      if (isEndpoint(node.getKind())) {
        validateEndpoint(graph, node, report);
      }
      if (isConnection(node.getKind())) {
        validateConnection(graph, node, report);
      }
      if (node.getKind() == EngineeringNode.Kind.BOUNDARY
          && Boolean.FALSE.equals(node.getProperties().get("resolved"))) {
        report.addWarning("ENG-TOPOLOGY-010", ARTIFACT, nodePath(node),
            "Boundary is unresolved: " + node.getExternalKey());
      }
      if (node.getKind() == EngineeringNode.Kind.LINE && node.getProperties().containsKey("lineTag")
          && countEdges(graph, EngineeringEdge.Kind.PART_OF_LINE, null, node.getId()) == 0) {
        report.addWarning("ENG-TOPOLOGY-011", ARTIFACT, nodePath(node),
            "Controlled line has no canonical pipe segment: " + node.getExternalKey());
      }
    }
    for (EngineeringEdge edge : graph.getEdges().values()) {
      validatePhysicalEdge(graph, edge, report);
    }
    return report;
  }

  private static void validateEndpoint(EngineeringGraph graph, EngineeringNode node,
      EngineeringPackageValidationReport report) {
    int owners = countEdges(graph, EngineeringEdge.Kind.HAS_PORT, null, node.getId());
    if (owners != 1) {
      report.addError("ENG-TOPOLOGY-002", ARTIFACT, nodePath(node),
          "Endpoint must have exactly one HAS_PORT owner; found " + owners);
    } else {
      EngineeringNode owner = ownerOf(graph, node.getId());
      if (!validOwner(node.getKind(), owner)) {
        report.addError("ENG-TOPOLOGY-003", ARTIFACT, nodePath(node), "Endpoint kind " + node.getKind()
            + " is not valid for owner kind " + (owner == null ? "UNKNOWN" : owner.getKind()));
      }
    }
    if (node.getKind() == EngineeringNode.Kind.PROCESS_TAP) {
      int measurements = countEdges(graph, EngineeringEdge.Kind.MEASURES, node.getId(), null);
      if (measurements != 1) {
        report.addError("ENG-TOPOLOGY-008", ARTIFACT, nodePath(node),
            "Process tap must measure exactly one canonical line; found " + measurements);
      }
      return;
    }
    int incoming = countFlowEdges(graph, null, node.getId());
    int outgoing = countFlowEdges(graph, node.getId(), null);
    String direction = stringProperty(node, "direction");
    if (Boolean.TRUE.equals(node.getProperties().get("directionConflict"))) {
      report.addError("ENG-TOPOLOGY-006", ARTIFACT, nodePath(node),
          "Endpoint is declared as both an inlet and an outlet without an explicit bidirectional definition");
    } else if ("OUTLET".equals(direction) && incoming > 0) {
      report.addError("ENG-TOPOLOGY-006", ARTIFACT, nodePath(node), "OUTLET endpoint has incoming flow relationships");
    } else if ("INLET".equals(direction) && outgoing > 0) {
      report.addError("ENG-TOPOLOGY-006", ARTIFACT, nodePath(node), "INLET endpoint has outgoing flow relationships");
    }
    if (incoming + outgoing == 0) {
      report.addWarning("ENG-TOPOLOGY-007", ARTIFACT, nodePath(node),
          "Endpoint is not connected to a process, signal or energy segment");
    }
  }

  private static void validateConnection(EngineeringGraph graph, EngineeringNode node,
      EngineeringPackageValidationReport report) {
    EngineeringEdge.Kind expectedFlow = flowKind(node.getKind());
    int incoming = countEdges(graph, expectedFlow, null, node.getId());
    int outgoing = countEdges(graph, expectedFlow, node.getId(), null);
    if (incoming != 1 || outgoing != 1) {
      report.addError("ENG-TOPOLOGY-004", ARTIFACT, nodePath(node),
          "Connection must have one incoming and one outgoing " + expectedFlow + " edge; found " + incoming + " and "
              + outgoing);
    }
    for (EngineeringEdge edge : graph.getEdges().values()) {
      if ((node.getId().equals(edge.getSourceId()) || node.getId().equals(edge.getTargetId()))
          && isFlowEdge(edge.getKind()) && edge.getKind() != expectedFlow) {
        report.addError("ENG-TOPOLOGY-005", ARTIFACT, edgePath(edge),
            "Connection kind " + node.getKind() + " cannot participate in " + edge.getKind());
      }
    }
  }

  private static void validatePhysicalEdge(EngineeringGraph graph, EngineeringEdge edge,
      EngineeringPackageValidationReport report) {
    EngineeringNode source = graph.getNode(edge.getSourceId());
    EngineeringNode target = graph.getNode(edge.getTargetId());
    if (source == null || target == null) {
      return;
    }
    if (isFlowEdge(edge.getKind())) {
      boolean sourceToConnection = isFlowEndpoint(source.getKind()) && isConnection(target.getKind());
      boolean connectionToTarget = isConnection(source.getKind()) && isFlowEndpoint(target.getKind());
      if (!sourceToConnection && !connectionToTarget) {
        report.addError("ENG-TOPOLOGY-005", ARTIFACT, edgePath(edge),
            "Flow edge must connect an endpoint and a connection segment");
      }
    } else if (edge.getKind() == EngineeringEdge.Kind.PART_OF_LINE
        && (source.getKind() != EngineeringNode.Kind.PIPE_SEGMENT || target.getKind() != EngineeringNode.Kind.LINE)) {
      report.addError("ENG-TOPOLOGY-009", ARTIFACT, edgePath(edge),
          "PART_OF_LINE must connect a PIPE_SEGMENT to a LINE");
    } else if (edge.getKind() == EngineeringEdge.Kind.MEASURES
        && (source.getKind() != EngineeringNode.Kind.PROCESS_TAP || target.getKind() != EngineeringNode.Kind.LINE)) {
      report.addError("ENG-TOPOLOGY-008", ARTIFACT, edgePath(edge), "MEASURES must connect a PROCESS_TAP to a LINE");
    }
  }

  private static EngineeringNode ownerOf(EngineeringGraph graph, String endpointId) {
    for (EngineeringEdge edge : graph.getEdges().values()) {
      if (edge.getKind() == EngineeringEdge.Kind.HAS_PORT && endpointId.equals(edge.getTargetId())) {
        return graph.getNode(edge.getSourceId());
      }
    }
    return null;
  }

  private static boolean validOwner(EngineeringNode.Kind endpointKind, EngineeringNode owner) {
    if (owner == null) {
      return false;
    }
    if (endpointKind == EngineeringNode.Kind.NOZZLE) {
      return owner.getKind() == EngineeringNode.Kind.EQUIPMENT;
    }
    if (endpointKind == EngineeringNode.Kind.PROCESS_TAP) {
      return owner.getKind() == EngineeringNode.Kind.INSTRUMENT;
    }
    return owner.getKind() == EngineeringNode.Kind.EQUIPMENT || owner.getKind() == EngineeringNode.Kind.LINE
        || owner.getKind() == EngineeringNode.Kind.BOUNDARY || owner.getKind() == EngineeringNode.Kind.INSTRUMENT;
  }

  private static int countFlowEdges(EngineeringGraph graph, String sourceId, String targetId) {
    int count = 0;
    for (EngineeringEdge edge : graph.getEdges().values()) {
      if (isFlowEdge(edge.getKind()) && matches(edge, sourceId, targetId)) {
        count++;
      }
    }
    return count;
  }

  private static int countEdges(EngineeringGraph graph, EngineeringEdge.Kind kind, String sourceId, String targetId) {
    int count = 0;
    for (EngineeringEdge edge : graph.getEdges().values()) {
      if (edge.getKind() == kind && matches(edge, sourceId, targetId)) {
        count++;
      }
    }
    return count;
  }

  private static boolean matches(EngineeringEdge edge, String sourceId, String targetId) {
    return (sourceId == null || sourceId.equals(edge.getSourceId()))
        && (targetId == null || targetId.equals(edge.getTargetId()));
  }

  private static boolean isEndpoint(EngineeringNode.Kind kind) {
    return kind == EngineeringNode.Kind.PORT || kind == EngineeringNode.Kind.NOZZLE
        || kind == EngineeringNode.Kind.PROCESS_TAP;
  }

  private static boolean isFlowEndpoint(EngineeringNode.Kind kind) {
    return kind == EngineeringNode.Kind.PORT || kind == EngineeringNode.Kind.NOZZLE;
  }

  private static boolean isConnection(EngineeringNode.Kind kind) {
    return kind == EngineeringNode.Kind.PIPE_SEGMENT || kind == EngineeringNode.Kind.SIGNAL_CONNECTION
        || kind == EngineeringNode.Kind.ENERGY_CONNECTION;
  }

  private static boolean isFlowEdge(EngineeringEdge.Kind kind) {
    return kind == EngineeringEdge.Kind.PROCESS_FLOW || kind == EngineeringEdge.Kind.SIGNAL_FLOW
        || kind == EngineeringEdge.Kind.ENERGY_FLOW;
  }

  private static EngineeringEdge.Kind flowKind(EngineeringNode.Kind connectionKind) {
    if (connectionKind == EngineeringNode.Kind.SIGNAL_CONNECTION) {
      return EngineeringEdge.Kind.SIGNAL_FLOW;
    }
    if (connectionKind == EngineeringNode.Kind.ENERGY_CONNECTION) {
      return EngineeringEdge.Kind.ENERGY_FLOW;
    }
    return EngineeringEdge.Kind.PROCESS_FLOW;
  }

  private static String stringProperty(EngineeringNode node, String name) {
    Object value = node.getProperties().get(name);
    return value == null ? "" : String.valueOf(value);
  }

  private static String nodePath(EngineeringNode node) {
    return "/nodes/" + node.getId();
  }

  private static String edgePath(EngineeringEdge edge) {
    return "/edges/" + edge.getId();
  }
}
