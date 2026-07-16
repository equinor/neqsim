package neqsim.process.engineering.model;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.engineering.EngineeringBoundary;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.EngineeringRequirement;
import neqsim.process.engineering.LineDesignInput;
import neqsim.process.engineering.designcase.EngineeringDesignCase;
import neqsim.process.engineering.designcase.EngineeringMetric;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;
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
        .putProperty("processName", project.getProcessSystem().getName())
        .putProperty("jurisdiction", project.getDesignBasis().getJurisdiction())
        .putProperty("facilityType", project.getDesignBasis().getFacilityType())
        .putProperty("projectPhase", project.getDesignBasis().getProjectPhase()).addProvenance(
            new EngineeringProvenance("PROJECT", project.getProjectId()).setApprovalStatus("REVIEW_REQUIRED"));
    graph.addNode(projectNode);

    Map<String, String> processElementIds = addProcessElements(project, graph, projectNodeId);
    addConnections(project, graph, processElementIds);
    addLines(project, graph, projectNodeId, processElementIds);
    addInstruments(project, graph, projectNodeId);
    addRequirements(project, graph, projectNodeId, processElementIds);
    addBoundaries(project, graph, projectNodeId, processElementIds);
    addDesignCases(project, graph, projectNodeId);
    List<EngineeringCalculation> allCalculations = new ArrayList<EngineeringCalculation>(project.getCalculations());
    allCalculations.addAll(additionalCalculations);
    addCalculations(allCalculations, graph, projectNodeId);
    return graph;
  }

  private static Map<String, String> addProcessElements(EngineeringProject project, EngineeringGraph graph,
      String projectNodeId) {
    Map<String, String> ids = new LinkedHashMap<String, String>();
    for (ProcessEquipmentInterface unit : project.getProcessSystem().getUnitOperations()) {
      if (unit == null || unit.getName() == null || unit.getName().trim().isEmpty()) {
        continue;
      }
      EngineeringNode.Kind kind = unit instanceof Stream ? EngineeringNode.Kind.LINE : EngineeringNode.Kind.EQUIPMENT;
      String nodeId = EngineeringIds.nodeId(kind, unit.getName());
      EngineeringNode node = new EngineeringNode(nodeId, kind, unit.getName(), unit.getName())
          .putProperty("javaClass", unit.getClass().getName()).putProperty("source", "PROCESS_SYSTEM")
          .addProvenance(new EngineeringProvenance("SIMULATION_MODEL", project.getProcessSystem().getName())
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
      graph.addNode(node);
      addEdge(graph, EngineeringEdge.Kind.CONTAINS, projectNodeId, nodeId, "processElement");
      ids.put(unit.getName(), nodeId);
    }
    return ids;
  }

  private static void addConnections(EngineeringProject project, EngineeringGraph graph,
      Map<String, String> processElementIds) {
    for (ProcessConnection connection : project.getProcessSystem().getConnections()) {
      String sourceId = processElementIds.get(connection.getSourceEquipment());
      String targetId = processElementIds.get(connection.getTargetEquipment());
      if (sourceId != null && targetId != null) {
        addEdge(graph, EngineeringEdge.Kind.CONNECTS_TO, sourceId, targetId,
            connection.getType().name() + ":" + connection.getSourcePort() + ":" + connection.getTargetPort());
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
    for (MeasurementDeviceInterface instrument : project.getProcessSystem().getMeasurementDevices()) {
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
    for (EngineeringCalculation calculation : calculations) {
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
          .putProperty("message", calculation.getMessage()).putProperty("inputs", calculation.toMap().get("inputs"))
          .addProvenance(
              new EngineeringProvenance("CALCULATION", calculation.getId()).setMethod(calculation.getMethod())
                  .setDesignCaseId(calculation.getDesignCaseId()).setApprovalStatus(calculation.getStatus().name()));
      for (String evidence : calculation.getEvidenceReferences()) {
        node.addProvenance(new EngineeringProvenance("EVIDENCE", evidence));
      }
      graph.addNode(node);
      addEdge(graph, EngineeringEdge.Kind.CONTAINS, projectNodeId, nodeId, "calculation");
      addEdge(graph, EngineeringEdge.Kind.GOVERNS, nodeId, calculation.getSubjectNodeId(), "result");
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
}
