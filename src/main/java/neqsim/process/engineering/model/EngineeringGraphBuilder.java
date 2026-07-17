package neqsim.process.engineering.model;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.engineering.EngineeringBoundary;
import neqsim.process.engineering.EngineeringApprovalRecord;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.design.EngineeringDesignValue;
import neqsim.process.engineering.EngineeringRequirement;
import neqsim.process.engineering.LineDesignInput;
import neqsim.process.engineering.designcase.EngineeringDesignCase;
import neqsim.process.engineering.designcase.EngineeringMetric;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;
import neqsim.process.measurementdevice.StreamMeasurementDeviceBaseClass;
import neqsim.process.processmodel.ProcessConnection;

/** Materializes an {@link EngineeringProject} as the canonical engineering graph. */
public final class EngineeringGraphBuilder {
  private EngineeringGraphBuilder() {
  }

  public static EngineeringGraph fromProject(EngineeringProject project) {
    return fromProject(project, new ArrayList<EngineeringCalculation>());
  }

  /** Builds a graph and includes transient calculation records such as a freshly executed design envelope. */
  public static EngineeringGraph fromProject(EngineeringProject project,
      List<EngineeringCalculation> additionalCalculations) {
    if (project == null) {
      throw new IllegalArgumentException("project must not be null");
    }
    if (additionalCalculations == null) {
      throw new IllegalArgumentException("additionalCalculations must not be null");
    }
    EngineeringGraph graph = new EngineeringGraph(project.getProjectId(), project.getRevision());
    String projectNodeId = EngineeringIds.nodeId(EngineeringNode.Kind.PROJECT, project.getProjectId());
    EngineeringNode projectNode = new EngineeringNode(projectNodeId, EngineeringNode.Kind.PROJECT,
        project.getProjectId(), project.getName()).putProperty("revision", project.getRevision())
        .putProperty("processName", project.getEngineeringProcessSystem().getName())
        .putProperty("jurisdiction", project.getDesignBasis().getJurisdiction())
        .putProperty("facilityType", project.getDesignBasis().getFacilityType())
        .putProperty("projectPhase", project.getDesignBasis().getProjectPhase()).addProvenance(
            new EngineeringProvenance("PROJECT", project.getProjectId()).setApprovalStatus("REVIEW_REQUIRED"));
    if (project.getLatestEngineeringDesignLoopResult() != null) {
      projectNode.putProperty("engineeringDesignLoopConverged",
          Boolean.valueOf(project.getLatestEngineeringDesignLoopResult().isConverged()));
      projectNode.putProperty("engineeringDesignIterationCount",
          Double.valueOf(project.getLatestEngineeringDesignLoopResult().getIterations().size()));
    }
    if (project.getProductionReadinessBasis() != null
        && project.getProductionReadinessBasis().getAutoConfigurationResult() != null) {
      projectNode.putProperty("engineeringConfigurationFingerprint", project.getProductionReadinessBasis()
          .getAutoConfigurationResult().getConfigurationFingerprint());
      projectNode.putProperty("engineeringConfigurationExecutionReady",
          Boolean.valueOf(project.getProductionReadinessBasis().getAutoConfigurationResult().isExecutionReady()));
      projectNode.putProperty("engineeringModuleDependencies",
          project.getProductionReadinessBasis().getAutoConfigurationResult().getModuleDependencies());
    }
    graph.addNode(projectNode);

    Map<String, String> processElementIds = addProcessElements(project, graph, projectNodeId);
    addLines(project, graph, projectNodeId, processElementIds);
    addConnections(project, graph, projectNodeId, processElementIds);
    addInstruments(project, graph, projectNodeId);
    addRequirements(project, graph, projectNodeId, processElementIds);
    addBoundaries(project, graph, projectNodeId, processElementIds);
    addDesignCases(project, graph, projectNodeId);
    List<EngineeringCalculation> allCalculations = new ArrayList<EngineeringCalculation>(project.getCalculations());
    allCalculations.addAll(additionalCalculations);
    addCalculations(allCalculations, graph, projectNodeId);
    addApprovals(project, graph, projectNodeId);
    return graph;
  }

  private static void addApprovals(EngineeringProject project, EngineeringGraph graph, String projectNodeId) {
    for (EngineeringApprovalRecord approval : project.getApprovalRecords()) {
      String nodeId = EngineeringIds.nodeId(EngineeringNode.Kind.APPROVAL, approval.getId());
      EngineeringNode node = new EngineeringNode(nodeId, EngineeringNode.Kind.APPROVAL, approval.getId(),
          approval.getDiscipline() + " approval for " + approval.getSubjectNodeId())
          .putProperty("discipline", approval.getDiscipline()).putProperty("status", approval.getStatus().name())
          .putProperty("reviewer", approval.getReviewer()).putProperty("reviewReference", approval.getReviewReference())
          .putProperty("effectiveDate", approval.getEffectiveDate())
          .putProperty("supersedesRecordId", approval.getSupersedesRecordId());
      graph.addNode(node);
      addEdge(graph, EngineeringEdge.Kind.CONTAINS, projectNodeId, nodeId, "approvalRecord");
      addEdge(graph, EngineeringEdge.Kind.APPROVES, nodeId, approval.getSubjectNodeId(), approval.getDiscipline());
      if (!approval.getSupersedesRecordId().isEmpty()) {
        String previousId = EngineeringIds.nodeId(EngineeringNode.Kind.APPROVAL, approval.getSupersedesRecordId());
        addEdge(graph, EngineeringEdge.Kind.SUPERSEDES, nodeId, previousId, "approvalHistory");
      }
    }
  }

  private static Map<String, String> addProcessElements(EngineeringProject project, EngineeringGraph graph,
      String projectNodeId) {
    Map<String, String> ids = new LinkedHashMap<String, String>();
    for (ProcessEquipmentInterface unit : project.getEngineeringProcessSystem().getUnitOperations()) {
      if (unit == null || unit.getName() == null || unit.getName().trim().isEmpty()) {
        continue;
      }
      EngineeringNode.Kind kind = unit instanceof Stream ? EngineeringNode.Kind.LINE : EngineeringNode.Kind.EQUIPMENT;
      String nodeId = EngineeringIds.nodeId(kind, unit.getName());
      EngineeringNode node = new EngineeringNode(nodeId, kind, unit.getName(), unit.getName())
          .putProperty("javaClass", unit.getClass().getName()).putProperty("physicalCategory", physicalCategory(unit))
          .putProperty("source", "PROCESS_SYSTEM")
          .addProvenance(new EngineeringProvenance("SIMULATION_MODEL", project.getEngineeringProcessSystem().getName())
              .setMethod("ProcessSystem topology"));
      try {
        node.putProperty("pressureBara", unit.getPressure("bara"));
      } catch (Exception ex) {
        node.putProperty("pressureStatus", "NOT_AVAILABLE");
      }
      try {
        node.putProperty("temperatureC", unit.getTemperature("C"));
      } catch (Exception ex) {
        node.putProperty("temperatureStatus", "NOT_AVAILABLE");
      }
      addEngineeringDesignValues(project, unit.getName(), node);
      graph.addNode(node);
      addEdge(graph, EngineeringEdge.Kind.CONTAINS, projectNodeId, nodeId, "processElement");
      ids.put(unit.getName(), nodeId);
    }
    return ids;
  }

  private static void addEngineeringDesignValues(EngineeringProject project, String equipmentTag,
      EngineeringNode node) {
    if (project.getLatestEngineeringDesignLoopResult() == null) {
      return;
    }
    String prefix = equipmentTag + ".";
    for (Map.Entry<String, EngineeringDesignValue> entry : project.getLatestEngineeringDesignLoopResult().getState()
        .getValues().entrySet()) {
      if (entry.getKey().startsWith(prefix)) {
        String property = entry.getKey().substring(prefix.length());
        node.putProperty("design." + property + ".value", Double.valueOf(entry.getValue().getValue()));
        node.putProperty("design." + property + ".unit", entry.getValue().getUnit());
      }
    }
  }

  private static void addConnections(EngineeringProject project, EngineeringGraph graph, String projectNodeId,
      Map<String, String> processElementIds) {
    for (ProcessConnection connection : project.getEngineeringProcessSystem().getConnections()) {
      String sourceId = processElementIds.get(connection.getSourceEquipment());
      String targetId = processElementIds.get(connection.getTargetEquipment());
      if (sourceId != null && targetId != null) {
        addEdge(graph, EngineeringEdge.Kind.CONNECTS_TO, sourceId, targetId,
            connection.getType().name() + ":" + connection.getSourcePort() + ":" + connection.getTargetPort());
        EngineeringNode sourceOwner = graph.getNode(sourceId);
        EngineeringNode targetOwner = graph.getNode(targetId);
        EngineeringNode.Kind sourcePortKind = endpointKind(connection.getType(), sourceOwner);
        EngineeringNode.Kind targetPortKind = endpointKind(connection.getType(), targetOwner);
        String sourcePortId = ensureEndpoint(graph, sourceId, sourcePortKind,
            connection.getSourceEquipment() + "." + connection.getSourcePort(), connection.getSourceEquipment(),
            connection.getSourcePort(), "OUTLET", connection.getType().name(),
            connection.getSourceReferenceDesignation());
        String targetPortId = ensureEndpoint(graph, targetId, targetPortKind,
            connection.getTargetEquipment() + "." + connection.getTargetPort(), connection.getTargetEquipment(),
            connection.getTargetPort(), "INLET", connection.getType().name(),
            connection.getTargetReferenceDesignation());
        String connectionKey = connection.getType().name() + ":" + connection.getSourceEquipment() + "."
            + connection.getSourcePort() + "->" + connection.getTargetEquipment() + "." + connection.getTargetPort();
        EngineeringNode.Kind connectionKind = connectionNodeKind(connection.getType());
        String connectionId = EngineeringIds.nodeId(connectionKind, connectionKey);
        if (graph.getNode(connectionId) == null) {
          graph.addNode(new EngineeringNode(connectionId, connectionKind, connectionKey, connection.toString())
              .putProperty("connectionType", connection.getType().name()).putProperty("sourceEndpointId", sourcePortId)
              .putProperty("targetEndpointId", targetPortId)
              .addProvenance(new EngineeringProvenance("PROCESS_CONNECTION", connection.toString())
                  .setMethod("ProcessSystem explicit connection metadata")));
          addEdge(graph, EngineeringEdge.Kind.CONTAINS, projectNodeId, connectionId, "physicalConnection");
        }
        EngineeringEdge.Kind flowKind = flowEdgeKind(connection.getType());
        addEdgeIfAbsent(graph, flowKind, sourcePortId, connectionId, "source");
        addEdgeIfAbsent(graph, flowKind, connectionId, targetPortId, "target");
        if (connection.getType() == ProcessConnection.ConnectionType.MATERIAL) {
          associateWithControlledLines(project, graph, connection, connectionId);
        }
      }
    }
  }

  private static void addLines(EngineeringProject project, EngineeringGraph graph, String projectNodeId,
      Map<String, String> processElementIds) {
    for (LineDesignInput line : project.getLineDesignInputs()) {
      String nodeId = EngineeringIds.nodeId(EngineeringNode.Kind.LINE, line.getLineTag());
      EngineeringNode node = new EngineeringNode(nodeId, EngineeringNode.Kind.LINE, line.getLineTag(),
          line.getLineTag()).putProperty("equipmentTag", line.getEquipmentTag())
          .putProperty("nominalPipeSize", line.getNominalPipeSize()).putProperty("schedule", line.getSchedule())
          .putProperty("materialGrade", line.getMaterialGrade()).putProperty("pipingClass", line.getPipingClass())
          .putProperty("designPressureBara", line.getDesignPressureBara())
          .putProperty("designTemperatureC", line.getDesignTemperatureC())
          .addProvenance(new EngineeringProvenance("CONTROLLED_LINE_LIST", line.getLineTag())
              .setApprovalStatus(line.getMissingFields().isEmpty() ? "REVIEW_REQUIRED" : "INCOMPLETE"));
      for (String evidence : line.getEvidenceReferences()) {
        node.addProvenance(new EngineeringProvenance("EVIDENCE", evidence));
      }
      EngineeringNode existingNode = graph.getNode(nodeId);
      if (existingNode == null) {
        graph.addNode(node);
        addEdge(graph, EngineeringEdge.Kind.CONTAINS, projectNodeId, nodeId, "controlledLine");
      } else {
        existingNode.putProperty("lineTag", line.getLineTag()).putProperty("equipmentTag", line.getEquipmentTag())
            .putProperty("nominalPipeSize", line.getNominalPipeSize()).putProperty("schedule", line.getSchedule())
            .putProperty("materialGrade", line.getMaterialGrade()).putProperty("pipingClass", line.getPipingClass())
            .putProperty("designPressureBara", line.getDesignPressureBara())
            .putProperty("designTemperatureC", line.getDesignTemperatureC());
      }
      String equipmentId = processElementIds.get(line.getEquipmentTag());
      if (equipmentId != null) {
        addEdge(graph, EngineeringEdge.Kind.APPLIES_TO, nodeId, equipmentId, "hydraulicModel");
      }
    }
  }

  private static void addInstruments(EngineeringProject project, EngineeringGraph graph, String projectNodeId) {
    for (MeasurementDeviceInterface instrument : project.getEngineeringProcessSystem().getMeasurementDevices()) {
      String tag = instrument.getTag();
      if (tag == null || tag.trim().isEmpty()) {
        tag = instrument.getName();
      }
      if (tag == null || tag.trim().isEmpty()) {
        continue;
      }
      String nodeId = EngineeringIds.nodeId(EngineeringNode.Kind.INSTRUMENT, tag);
      EngineeringNode node = new EngineeringNode(nodeId, EngineeringNode.Kind.INSTRUMENT, tag, tag)
          .putProperty("name", instrument.getName()).putProperty("unit", instrument.getUnit())
          .putProperty("javaClass", instrument.getClass().getName())
          .addProvenance(new EngineeringProvenance("PROCESS_INSTRUMENT", instrument.getName()));
      graph.addNode(node);
      addEdge(graph, EngineeringEdge.Kind.CONTAINS, projectNodeId, nodeId, "instrument");
      if (instrument instanceof StreamMeasurementDeviceBaseClass) {
        StreamMeasurementDeviceBaseClass streamInstrument = (StreamMeasurementDeviceBaseClass) instrument;
        if (streamInstrument.getStream() != null && streamInstrument.getStream().getName() != null) {
          String streamId = EngineeringIds.nodeId(EngineeringNode.Kind.LINE, streamInstrument.getStream().getName());
          if (graph.getNode(streamId) != null) {
            String tapKey = tag + ".processTap";
            String tapId = EngineeringIds.nodeId(EngineeringNode.Kind.PROCESS_TAP, tapKey);
            graph.addNode(new EngineeringNode(tapId, EngineeringNode.Kind.PROCESS_TAP, tapKey, tag + " process tap")
                .putProperty("instrumentTag", tag).putProperty("streamTag", streamInstrument.getStream().getName())
                .putProperty("direction", "SENSE")
                .addProvenance(new EngineeringProvenance("PROCESS_INSTRUMENT", instrument.getName())
                    .setMethod("StreamMeasurementDeviceBaseClass measured stream")));
            addEdge(graph, EngineeringEdge.Kind.HAS_PORT, nodeId, tapId, "processTap");
            addEdge(graph, EngineeringEdge.Kind.MEASURES, tapId, streamId, "processMeasurement");
          }
        }
      }
    }
  }

  private static void addRequirements(EngineeringProject project, EngineeringGraph graph, String projectNodeId,
      Map<String, String> processElementIds) {
    for (EngineeringRequirement requirement : project.getRequirements()) {
      String nodeId = EngineeringIds.nodeId(EngineeringNode.Kind.REQUIREMENT, requirement.getId());
      EngineeringNode node = new EngineeringNode(nodeId, EngineeringNode.Kind.REQUIREMENT, requirement.getId(),
          requirement.getTitle()).putProperty("type", requirement.getType().name())
          .putProperty("equipmentTag", requirement.getEquipmentTag())
          .putProperty("rationale", requirement.getRationale()).putProperty("silTarget", requirement.getSilTarget())
          .putProperty("approvalStatus", requirement.getApprovalStatus().name())
          .addProvenance(new EngineeringProvenance(requirement.getOrigin().name(), requirement.getId())
              .setApprovalStatus(requirement.getApprovalStatus().name())
              .addEvidenceReference(requirement.getReviewRecord()));
      graph.addNode(node);
      addEdge(graph, EngineeringEdge.Kind.CONTAINS, projectNodeId, nodeId, "requirement");
      String equipmentId = processElementIds.get(requirement.getEquipmentTag());
      if (equipmentId != null) {
        addEdge(graph, EngineeringEdge.Kind.APPLIES_TO, nodeId, equipmentId, requirement.getType().name());
      }
    }
  }

  private static void addBoundaries(EngineeringProject project, EngineeringGraph graph, String projectNodeId,
      Map<String, String> processElementIds) {
    for (EngineeringBoundary boundary : project.getBoundaries()) {
      String nodeId = EngineeringIds.nodeId(EngineeringNode.Kind.BOUNDARY, boundary.getId());
      EngineeringNode node = new EngineeringNode(nodeId, EngineeringNode.Kind.BOUNDARY, boundary.getId(),
          boundary.getId()).putProperty("type", boundary.getType().name())
          .putProperty("equipmentTag", boundary.getEquipmentTag()).putProperty("resolved", boundary.isResolved())
          .putProperty("connectedDocumentReference", boundary.getConnectedDocumentReference())
          .addProvenance(new EngineeringProvenance("BOUNDARY_REGISTER", boundary.getId())
              .setApprovalStatus(boundary.isResolved() ? "REVIEW_REQUIRED" : "INCOMPLETE")
              .addEvidenceReference(boundary.getEvidenceReference()));
      graph.addNode(node);
      addEdge(graph, EngineeringEdge.Kind.CONTAINS, projectNodeId, nodeId, "boundary");
      String equipmentId = processElementIds.get(boundary.getEquipmentTag());
      if (equipmentId != null) {
        addEdge(graph, EngineeringEdge.Kind.CONNECTS_TO, equipmentId, nodeId, boundary.getType().name());
        addBoundaryTopology(graph, projectNodeId, boundary, nodeId, equipmentId);
      }
    }
  }

  private static void addDesignCases(EngineeringProject project, EngineeringGraph graph, String projectNodeId) {
    for (EngineeringDesignCase designCase : project.getExecutableDesignCases()) {
      String nodeId = EngineeringIds.nodeId(EngineeringNode.Kind.DESIGN_CASE, designCase.getId());
      EngineeringNode node = new EngineeringNode(nodeId, EngineeringNode.Kind.DESIGN_CASE, designCase.getId(),
          designCase.getName()).putProperty("type", designCase.getType().name())
          .putProperty("description", designCase.getDescription())
          .putProperty("approvalStatus", designCase.getApprovalStatus())
          .putProperty("caseGroup", designCase.getCaseGroup())
          .putProperty("required", Boolean.valueOf(designCase.isRequired()))
          .putProperty("enabled", Boolean.valueOf(designCase.isEnabled()))
          .putProperty("priority", String.valueOf(designCase.getPriority()))
          .addProvenance(new EngineeringProvenance("DESIGN_CASE_REGISTER", designCase.getId())
              .setApprovalStatus(designCase.getApprovalStatus()));
      for (String evidence : designCase.getEvidenceReferences()) {
        node.addProvenance(new EngineeringProvenance("EVIDENCE", evidence));
      }
      graph.addNode(node);
      addEdge(graph, EngineeringEdge.Kind.CONTAINS, projectNodeId, nodeId, "designCase");
    }
    for (EngineeringMetric metric : project.getEngineeringMetrics()) {
      String equipmentId = EngineeringIds.nodeId(EngineeringNode.Kind.EQUIPMENT, metric.getSubjectTag());
      EngineeringNode equipment = graph.getNode(equipmentId);
      if (equipment != null) {
        equipment.putProperty("envelopeMetric:" + metric.getId(), metric.getGoverningDirection().name());
      }
    }
  }

  private static void addCalculations(List<EngineeringCalculation> calculations, EngineeringGraph graph,
      String projectNodeId) {
    EngineeringCalculationDag dag = EngineeringCalculationDag.from(calculations);
    for (EngineeringCalculation calculation : dag.getCalculationsInExecutionOrder()) {
      if (graph.getNode(calculation.getSubjectNodeId()) == null) {
        throw new IllegalArgumentException(
            "Calculation " + calculation.getId() + " references unknown subject " + calculation.getSubjectNodeId());
      }
      String nodeId = EngineeringIds.nodeId(EngineeringNode.Kind.CALCULATION, calculation.getId());
      EngineeringNode node = new EngineeringNode(nodeId, EngineeringNode.Kind.CALCULATION, calculation.getId(),
          calculation.getId()).putProperty("method", calculation.getMethod())
          .putProperty("status", calculation.getStatus().name())
          .putProperty("resultValue", calculation.getResultValue())
          .putProperty("resultUnit", calculation.getResultUnit())
          .putProperty("designCaseId", calculation.getDesignCaseId())
          .putProperty("standardReference", calculation.getStandardReference())
          .putProperty("standardReferences", calculation.toMap().get("standardReferences"))
          .putProperty("standardsRequired", Boolean.valueOf(calculation.isStandardsRequired()))
          .putProperty("standardsReady",
              Boolean.valueOf(!calculation.isStandardsRequired() || calculation.hasStandardsBasis()))
          .putProperty("executionReadiness", dag.getReadiness(calculation.getId()).name())
          .putProperty("message", calculation.getMessage()).putProperty("inputs", calculation.toMap().get("inputs"))
          .addProvenance(
              new EngineeringProvenance("CALCULATION", calculation.getId()).setMethod(calculation.getMethod())
                  .setDesignCaseId(calculation.getDesignCaseId()).setApprovalStatus(calculation.getStatus().name()));
      for (String evidence : calculation.getEvidenceReferences()) {
        node.addProvenance(new EngineeringProvenance("EVIDENCE", evidence));
      }
      graph.addNode(node);
      addEdge(graph, EngineeringEdge.Kind.CONTAINS, projectNodeId, nodeId, "calculation");
    }
    for (EngineeringCalculation calculation : dag.getCalculationsInExecutionOrder()) {
      String nodeId = EngineeringIds.nodeId(EngineeringNode.Kind.CALCULATION, calculation.getId());
      addEdge(graph, EngineeringEdge.Kind.GOVERNS, nodeId, calculation.getSubjectNodeId(), "result");
      for (String prerequisiteId : calculation.getPrerequisiteCalculationIds()) {
        String prerequisiteNodeId = EngineeringIds.nodeId(EngineeringNode.Kind.CALCULATION, prerequisiteId);
        addEdge(graph, EngineeringEdge.Kind.DEPENDS_ON, nodeId, prerequisiteNodeId, "prerequisiteCalculation");
      }
      for (EngineeringCalculation.Input input : calculation.getInputs()) {
        if (!input.getSourceNodeId().isEmpty() && graph.getNode(input.getSourceNodeId()) != null) {
          addEdge(graph, EngineeringEdge.Kind.DEPENDS_ON, nodeId, input.getSourceNodeId(), "input");
        }
      }
      if (!calculation.getDesignCaseId().isEmpty()) {
        String caseId = EngineeringIds.nodeId(EngineeringNode.Kind.DESIGN_CASE, calculation.getDesignCaseId());
        if (graph.getNode(caseId) != null) {
          addEdge(graph, EngineeringEdge.Kind.GENERATED_FROM, nodeId, caseId, "governingCase");
        }
      }
    }
  }

  private static void addEdge(EngineeringGraph graph, EngineeringEdge.Kind kind, String sourceId, String targetId,
      String role) {
    graph.addEdge(
        new EngineeringEdge(EngineeringIds.edgeId(kind, sourceId, targetId, role), sourceId, targetId, kind, role));
  }

  private static void addEdgeIfAbsent(EngineeringGraph graph, EngineeringEdge.Kind kind, String sourceId,
      String targetId, String role) {
    String edgeId = EngineeringIds.edgeId(kind, sourceId, targetId, role);
    if (!graph.getEdges().containsKey(edgeId)) {
      graph.addEdge(new EngineeringEdge(edgeId, sourceId, targetId, kind, role));
    }
  }

  private static String ensureEndpoint(EngineeringGraph graph, String ownerId, EngineeringNode.Kind kind,
      String externalKey, String equipmentTag, String portName, String direction, String connectionType,
      String referenceDesignation) {
    String endpointId = EngineeringIds.nodeId(kind, externalKey);
    EngineeringNode endpoint = graph.getNode(endpointId);
    if (endpoint == null) {
      endpoint = new EngineeringNode(endpointId, kind, externalKey, externalKey).putProperty("ownerNodeId", ownerId)
          .putProperty("equipmentTag", equipmentTag).putProperty("portName", portName)
          .putProperty("direction", direction).putProperty("connectionType", connectionType)
          .putProperty("referenceDesignation", referenceDesignation)
          .addProvenance(new EngineeringProvenance("PROCESS_CONNECTION", externalKey)
              .setMethod("Explicit equipment port metadata"));
      graph.addNode(endpoint);
    } else if (!direction.equals(endpoint.getProperties().get("direction"))) {
      endpoint.putProperty("direction", "BIDIRECTIONAL").putProperty("directionConflict", Boolean.TRUE);
    }
    addEdgeIfAbsent(graph, EngineeringEdge.Kind.HAS_PORT, ownerId, endpointId, portName);
    return endpointId;
  }

  private static EngineeringNode.Kind endpointKind(ProcessConnection.ConnectionType type, EngineeringNode owner) {
    if (type == ProcessConnection.ConnectionType.MATERIAL && owner != null
        && owner.getKind() == EngineeringNode.Kind.EQUIPMENT) {
      return EngineeringNode.Kind.NOZZLE;
    }
    return EngineeringNode.Kind.PORT;
  }

  private static EngineeringNode.Kind connectionNodeKind(ProcessConnection.ConnectionType type) {
    if (type == ProcessConnection.ConnectionType.SIGNAL) {
      return EngineeringNode.Kind.SIGNAL_CONNECTION;
    }
    if (type == ProcessConnection.ConnectionType.ENERGY) {
      return EngineeringNode.Kind.ENERGY_CONNECTION;
    }
    return EngineeringNode.Kind.PIPE_SEGMENT;
  }

  private static EngineeringEdge.Kind flowEdgeKind(ProcessConnection.ConnectionType type) {
    if (type == ProcessConnection.ConnectionType.SIGNAL) {
      return EngineeringEdge.Kind.SIGNAL_FLOW;
    }
    if (type == ProcessConnection.ConnectionType.ENERGY) {
      return EngineeringEdge.Kind.ENERGY_FLOW;
    }
    return EngineeringEdge.Kind.PROCESS_FLOW;
  }

  private static void associateWithControlledLines(EngineeringProject project, EngineeringGraph graph,
      ProcessConnection connection, String connectionId) {
    for (LineDesignInput line : project.getLineDesignInputs()) {
      if (line.getEquipmentTag().equals(connection.getSourceEquipment())
          || line.getEquipmentTag().equals(connection.getTargetEquipment())
          || line.getLineTag().equals(connection.getSourceEquipment())
          || line.getLineTag().equals(connection.getTargetEquipment())) {
        String lineId = EngineeringIds.nodeId(EngineeringNode.Kind.LINE, line.getLineTag());
        if (graph.getNode(lineId) != null) {
          addEdgeIfAbsent(graph, EngineeringEdge.Kind.PART_OF_LINE, connectionId, lineId, "controlledLine");
        }
      }
    }
  }

  private static void addBoundaryTopology(EngineeringGraph graph, String projectNodeId, EngineeringBoundary boundary,
      String boundaryId, String equipmentId) {
    boolean inbound = boundary.getType() == EngineeringBoundary.Type.PROCESS_INLET
        || boundary.getType() == EngineeringBoundary.Type.UTILITY_INLET;
    boolean bidirectional = boundary.getType() == EngineeringBoundary.Type.RECYCLE_TIE_IN;
    String boundaryDirection = bidirectional ? "BIDIRECTIONAL" : inbound ? "OUTLET" : "INLET";
    String equipmentDirection = bidirectional ? "BIDIRECTIONAL" : inbound ? "INLET" : "OUTLET";
    String boundaryPortId = ensureEndpoint(graph, boundaryId, EngineeringNode.Kind.PORT, boundary.getId() + ".tieIn",
        boundary.getId(), "tieIn", boundaryDirection, "MATERIAL", null);
    String equipmentPortId = ensureEndpoint(graph, equipmentId, EngineeringNode.Kind.NOZZLE,
        boundary.getEquipmentTag() + "." + boundary.getId(), boundary.getEquipmentTag(), boundary.getId(),
        equipmentDirection, "MATERIAL", null);
    String segmentKey = "BOUNDARY:" + boundary.getId();
    String segmentId = EngineeringIds.nodeId(EngineeringNode.Kind.PIPE_SEGMENT, segmentKey);
    graph.addNode(new EngineeringNode(segmentId, EngineeringNode.Kind.PIPE_SEGMENT, segmentKey,
        boundary.getId() + " boundary connection").putProperty("connectionType", "MATERIAL")
        .putProperty("boundaryId", boundary.getId()).putProperty("boundaryType", boundary.getType().name())
        .putProperty("sourceEndpointId", inbound ? boundaryPortId : equipmentPortId)
        .putProperty("targetEndpointId", inbound ? equipmentPortId : boundaryPortId)
        .addProvenance(new EngineeringProvenance("BOUNDARY_REGISTER", boundary.getId())
            .setMethod("Controlled document boundary topology")));
    addEdge(graph, EngineeringEdge.Kind.CONTAINS, projectNodeId, segmentId, "boundaryConnection");
    String sourcePortId = inbound ? boundaryPortId : equipmentPortId;
    String targetPortId = inbound ? equipmentPortId : boundaryPortId;
    addEdge(graph, EngineeringEdge.Kind.PROCESS_FLOW, sourcePortId, segmentId, "source");
    addEdge(graph, EngineeringEdge.Kind.PROCESS_FLOW, segmentId, targetPortId, "target");
  }

  private static String physicalCategory(ProcessEquipmentInterface unit) {
    if (unit instanceof Stream) {
      return "LINE";
    }
    String name = unit.getClass().getSimpleName().toLowerCase();
    if (name.contains("valve")) {
      return "VALVE";
    }
    if (name.contains("mixer") || name.contains("splitter") || name.contains("tee") || name.contains("reducer")) {
      return "FITTING";
    }
    return "EQUIPMENT";
  }
}
