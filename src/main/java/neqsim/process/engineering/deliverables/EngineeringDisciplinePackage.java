package neqsim.process.engineering.deliverables;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.EngineeringRequirement;
import neqsim.process.engineering.LineDesignInput;
import neqsim.process.engineering.designcase.EngineeringDesignEnvelope;
import neqsim.process.engineering.model.EngineeringGraph;
import neqsim.process.engineering.model.EngineeringIds;
import neqsim.process.engineering.model.EngineeringNode;
import neqsim.process.engineering.validation.EngineeringSchemaCatalog;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.Stream;

/** Builds a canonical, review-governed handoff organized for engineering discipline consumers. */
public final class EngineeringDisciplinePackage {
  private EngineeringDisciplinePackage() {
  }

  public static Map<String, Object> build(EngineeringProject project, EngineeringGraph graph,
      EngineeringDesignEnvelope envelope) {
    if (project == null || graph == null) {
      throw new IllegalArgumentException("project and graph are required");
    }
    List<Map<String, Object>> equipment = equipmentDatasheets(project, envelope);
    List<Map<String, Object>> lines = lineDatasheets(project);
    List<Map<String, Object>> requirements = requirementRegister(project);
    List<Map<String, Object>> deliverables = new ArrayList<Map<String, Object>>();
    deliverables.add(deliverable("PROCESS-EQUIPMENT-DATASHEETS", "PROCESS", "equipment-register.json", equipment));
    deliverables.add(
        deliverable("MECHANICAL-EQUIPMENT-DATASHEETS", "MECHANICAL", "engineering-discipline-package.json", equipment));
    deliverables.add(deliverable("PIPING-LINE-LIST", "PIPING", "line-register.json", lines));
    deliverables.add(deliverable("INSTRUMENT-INDEX", "INSTRUMENTATION_AUTOMATION", "instrument-register.json",
        filterRequirements(requirements, "INSTRUMENTATION_AUTOMATION")));
    deliverables.add(deliverable("SAFEGUARDING-REQUIREMENTS-REGISTER", "PROCESS_SAFETY",
        "engineering-discipline-package.json", filterRequirements(requirements, "PROCESS_SAFETY")));

    Map<String, Object> document = new LinkedHashMap<String, Object>();
    document.put("schemaVersion", EngineeringSchemaCatalog.DISCIPLINE_PACKAGE);
    document.put("schemaUri", EngineeringSchemaCatalog.schemaUri(EngineeringSchemaCatalog.DISCIPLINE_PACKAGE));
    document.put("projectId", project.getProjectId());
    document.put("revision", project.getRevision());
    document.put("graphFingerprint", graph.toMap().get("fingerprint"));
    document.put("deliverables", deliverables);
    document.put("equipmentDatasheets", equipment);
    document.put("lineDatasheets", lines);
    document.put("instrumentAndSafeguardingRequirements", requirements);
    Map<String, Object> summary = summarize(deliverables);
    document.put("disciplineSummary", summary);
    document.put("governance",
        "Generated discipline data remains review-required and is not approved or fit for construction");
    return document;
  }

  private static List<Map<String, Object>> equipmentDatasheets(EngineeringProject project,
      EngineeringDesignEnvelope envelope) {
    List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
    for (ProcessEquipmentInterface unit : project.getProcessSystem().getUnitOperations()) {
      if (unit == null || unit instanceof Stream) {
        continue;
      }
      Map<String, Object> row = new LinkedHashMap<String, Object>();
      row.put("id", unit.getName());
      row.put("graphNodeId", EngineeringIds.nodeId(EngineeringNode.Kind.EQUIPMENT, unit.getName()));
      row.put("equipmentType", unit.getClass().getSimpleName());
      putOperatingConditions(row, unit);
      List<Map<String, Object>> governing = governingValues(envelope, unit.getName());
      row.put("governingDesignValues", governing);
      List<String> missing = new ArrayList<String>();
      if (unit.getDesignConditions() == null || unit.getDesignConditions().isEmpty()) {
        missing.add("designConditions");
      } else {
        row.put("declaredDesignConditions", unit.getDesignConditions());
      }
      if (governing.isEmpty()) {
        missing.add("governingDesignValues");
      }
      row.put("missingFields", missing);
      row.put("readinessStatus", missing.isEmpty() ? "REVIEW_REQUIRED" : "INCOMPLETE");
      result.add(row);
    }
    return result;
  }

  private static List<Map<String, Object>> lineDatasheets(EngineeringProject project) {
    List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
    for (LineDesignInput input : project.getLineDesignInputs()) {
      Map<String, Object> row = new LinkedHashMap<String, Object>(input.toMap());
      row.put("id", input.getLineTag());
      row.put("graphNodeId", EngineeringIds.nodeId(EngineeringNode.Kind.LINE, input.getLineTag()));
      row.put("readinessStatus", input.getMissingFields().isEmpty() ? "REVIEW_REQUIRED" : "INCOMPLETE");
      result.add(row);
    }
    return result;
  }

  private static List<Map<String, Object>> requirementRegister(EngineeringProject project) {
    List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
    for (EngineeringRequirement requirement : project.getRequirements()) {
      Map<String, Object> row = new LinkedHashMap<String, Object>();
      row.put("id", requirement.getId());
      row.put("graphNodeId", EngineeringIds.nodeId(EngineeringNode.Kind.REQUIREMENT, requirement.getId()));
      row.put("discipline", discipline(requirement.getType()));
      row.put("equipmentTag", requirement.getEquipmentTag());
      row.put("requirementType", requirement.getType().name());
      row.put("title", requirement.getTitle());
      row.put("rationale", requirement.getRationale());
      row.put("standardReferences", requirement.getStandardReferences());
      row.put("silTarget", requirement.getSilTarget());
      row.put("origin", requirement.getOrigin().name());
      row.put("approvalStatus", requirement.getApprovalStatus().name());
      row.put("reviewRecord", requirement.getReviewRecord());
      List<String> missing = new ArrayList<String>();
      if (requirement.getStandardReferences().isEmpty()) {
        missing.add("standardReferences");
      }
      if ("SIL_UNASSIGNED".equals(requirement.getSilTarget())
          && requirement.getType() == EngineeringRequirement.Type.TRIP) {
        missing.add("silTargetOrNotSilRatedDecision");
      }
      row.put("missingFields", missing);
      row.put("readinessStatus", missing.isEmpty() ? "REVIEW_REQUIRED" : "INCOMPLETE");
      result.add(row);
    }
    return result;
  }

  private static Map<String, Object> deliverable(String id, String discipline, String artifact,
      List<Map<String, Object>> items) {
    int ready = 0;
    int missing = 0;
    List<String> itemIds = new ArrayList<String>();
    for (Map<String, Object> item : items) {
      itemIds.add(String.valueOf(item.get("id")));
      if ("REVIEW_REQUIRED".equals(item.get("readinessStatus"))) {
        ready++;
      }
      Object fields = item.get("missingFields");
      if (fields instanceof List<?>) {
        missing += ((List<?>) fields).size();
      }
    }
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("id", id);
    result.put("discipline", discipline);
    result.put("artifact", artifact);
    result.put("itemCount", Integer.valueOf(items.size()));
    result.put("readyForReviewCount", Integer.valueOf(ready));
    result.put("missingInputCount", Integer.valueOf(missing));
    result.put("status", items.isEmpty() ? "NOT_CONFIGURED" : missing == 0 ? "REVIEW_REQUIRED" : "INCOMPLETE");
    result.put("controlledItemIds", itemIds);
    return result;
  }

  private static List<Map<String, Object>> filterRequirements(List<Map<String, Object>> requirements,
      String discipline) {
    List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
    for (Map<String, Object> requirement : requirements) {
      if (discipline.equals(requirement.get("discipline"))) {
        result.add(requirement);
      }
    }
    return result;
  }

  private static Map<String, Object> summarize(List<Map<String, Object>> deliverables) {
    int incomplete = 0;
    int notConfigured = 0;
    int missing = 0;
    for (Map<String, Object> deliverable : deliverables) {
      if ("INCOMPLETE".equals(deliverable.get("status"))) {
        incomplete++;
      } else if ("NOT_CONFIGURED".equals(deliverable.get("status"))) {
        notConfigured++;
      }
      missing += ((Integer) deliverable.get("missingInputCount")).intValue();
    }
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("deliverableCount", Integer.valueOf(deliverables.size()));
    result.put("incompleteDeliverableCount", Integer.valueOf(incomplete));
    result.put("notConfiguredDeliverableCount", Integer.valueOf(notConfigured));
    result.put("missingInputCount", Integer.valueOf(missing));
    result.put("overallStatus", incomplete == 0 && notConfigured == 0 ? "REVIEW_REQUIRED" : "INCOMPLETE");
    return result;
  }

  private static List<Map<String, Object>> governingValues(EngineeringDesignEnvelope envelope, String equipmentTag) {
    List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
    if (envelope != null) {
      for (EngineeringDesignEnvelope.GoverningValue value : envelope.getGoverningValues().values()) {
        if (equipmentTag.equals(value.getMetric().getSubjectTag())) {
          result.add(value.toMap());
        }
      }
    }
    return result;
  }

  private static void putOperatingConditions(Map<String, Object> row, ProcessEquipmentInterface unit) {
    try {
      row.put("operatingPressureBara", Double.valueOf(unit.getPressure("bara")));
    } catch (Exception ex) {
      row.put("operatingPressureStatus", "NOT_AVAILABLE");
    }
    try {
      row.put("operatingTemperatureC", Double.valueOf(unit.getTemperature("C")));
    } catch (Exception ex) {
      row.put("operatingTemperatureStatus", "NOT_AVAILABLE");
    }
  }

  private static String discipline(EngineeringRequirement.Type type) {
    if (type == EngineeringRequirement.Type.CONTROL || type == EngineeringRequirement.Type.INSTRUMENT
        || type == EngineeringRequirement.Type.ALARM) {
      return "INSTRUMENTATION_AUTOMATION";
    }
    if (type == EngineeringRequirement.Type.TRIP || type == EngineeringRequirement.Type.RELIEF
        || type == EngineeringRequirement.Type.MECHANICAL_PROTECTION
        || type == EngineeringRequirement.Type.FIRE_AND_GAS) {
      return "PROCESS_SAFETY";
    }
    return "PROCESS";
  }
}
