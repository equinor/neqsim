package neqsim.process.engineering.deliverables;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.engineering.EngineeringBoundary;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.EngineeringRequirement;
import neqsim.process.engineering.EngineeringStandard;
import neqsim.process.engineering.ReliefDeviceDesignInput;
import neqsim.process.engineering.ShutdownSequence;
import neqsim.process.engineering.design.EngineeringDesignIteration;
import neqsim.process.engineering.design.EngineeringDesignValue;
import neqsim.process.engineering.designcase.EngineeringDesignEnvelope;
import neqsim.process.engineering.model.EngineeringGraph;
import neqsim.process.engineering.model.EngineeringGraphDiff;
import neqsim.process.engineering.model.EngineeringNode;
import neqsim.process.engineering.production.EngineeringProductionReadinessAssessment;
import neqsim.process.engineering.production.EngineeringProductionReadinessBasis;
import neqsim.process.engineering.production.EngineeringExternalEvidenceAssessment;
import neqsim.process.engineering.production.EngineeringExternalEvidenceDocumentIntegrity;
import neqsim.process.engineering.production.EngineeringExternalEvidenceRegister;
import neqsim.process.engineering.validation.EngineeringSchemaCatalog;

/** Builds coordinated, traceable engineering documents from the canonical project and graph. */
final class EngineeringCoordinatedPackage {
  private EngineeringCoordinatedPackage() {
  }

  static Map<String, Object> build(EngineeringProject project, EngineeringGraph graph,
      EngineeringDesignEnvelope envelope, EngineeringGraphDiff diff) {
    Map<String, Object> documents = new LinkedHashMap<String, Object>();
    documents.put("process-design-basis.json", designBasis(project));
    documents.put("equipment-datasheets.json", equipmentDatasheets(project, graph, envelope));
    documents.put("valve-list.json", valveList(project));
    documents.put("io-list.json", ioList(project));
    documents.put("alarm-trip-schedule.json", alarmTripSchedule(project));
    documents.put("shutdown-narratives.json", shutdownNarratives(project));
    documents.put("psv-datasheets.json", psvDatasheets(project));
    documents.put("flare-blowdown-report.json", flareBlowdownReport(project));
    documents.put("utility-summary.json", utilitySummary(project, envelope));
    documents.put("materials-selection-report.json", materialsReport(project));
    documents.put("engineering-external-evidence-register.json", externalEvidenceRegister(project));
    documents.put("unresolved-engineering-actions.json", unresolvedActions(project));
    documents.put("revision-impact-report.json", revisionImpact(project, diff));
    return documents;
  }

  private static Map<String, Object> designBasis(EngineeringProject project) {
    Map<String, Object> result = header(project, "neqsim_process_design_basis.v1");
    result.put("jurisdiction", project.getDesignBasis().getJurisdiction());
    result.put("facilityType", project.getDesignBasis().getFacilityType());
    result.put("projectPhase", project.getDesignBasis().getProjectPhase());
    result.put("designCases", project.getDesignBasis().getDesignCases());
    List<Map<String, Object>> standards = new ArrayList<Map<String, Object>>();
    for (EngineeringStandard standard : project.getDesignBasis().getStandards()) {
      Map<String, Object> row = new LinkedHashMap<String, Object>();
      row.put("code", standard.getCode());
      row.put("edition", standard.getEdition());
      row.put("title", standard.getTitle());
      row.put("application", standard.getApplication());
      standards.add(row);
    }
    result.put("standards", standards);
    result.put("approvalStatus", "REVIEW_REQUIRED");
    return result;
  }

  private static Map<String, Object> equipmentDatasheets(EngineeringProject project, EngineeringGraph graph,
      EngineeringDesignEnvelope envelope) {
    Map<String, Object> result = header(project, "neqsim_equipment_datasheets.v1");
    List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
    for (EngineeringNode node : graph.getNodes().values()) {
      if (node.getKind() != EngineeringNode.Kind.EQUIPMENT) {
        continue;
      }
      Map<String, Object> row = new LinkedHashMap<String, Object>();
      row.put("equipmentTag", node.getExternalKey());
      row.put("graphNodeId", node.getId());
      row.put("properties", node.getProperties());
      List<Map<String, Object>> governing = new ArrayList<Map<String, Object>>();
      if (envelope != null) {
        for (EngineeringDesignEnvelope.GoverningValue value : envelope.getGoverningValues().values()) {
          if (node.getExternalKey().equals(value.getMetric().getSubjectTag())) {
            governing.add(value.toMap());
          }
        }
      }
      row.put("governingValues", governing);
      row.put("calculationAndEvidenceReferences", node.getProvenance());
      row.put("approvalStatus", "REVIEW_REQUIRED");
      rows.add(row);
    }
    result.put("datasheets", rows);
    result.put("rowCount", Integer.valueOf(rows.size()));
    EngineeringProductionReadinessBasis basis = project.getProductionReadinessBasis();
    result.put("compressorProtectionQualification",
        basis == null || basis.getCompressorProtectionQualification() == null ? null
            : basis.getCompressorProtectionQualification().toMap());
    result.put("mechanicalIntegrityQualification",
        basis == null || basis.getMechanicalIntegrityQualification() == null ? null
            : basis.getMechanicalIntegrityQualification().toMap());
    return result;
  }

  private static Map<String, Object> valveList(EngineeringProject project) {
    Map<String, Object> result = header(project, "neqsim_valve_list.v1");
    List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
    if (project.getLatestEngineeringDesignLoopResult() != null) {
      for (EngineeringDesignValue value : project.getLatestEngineeringDesignLoopResult().getState().getValues()
          .values()) {
        if (value.getKey().endsWith(".selectedCv") || value.getKey().endsWith(".governingOpening")) {
          rows.add(value.toMap());
        }
      }
    }
    result.put("rows", rows);
    result.put("rowCount", Integer.valueOf(rows.size()));
    EngineeringProductionReadinessBasis basis = project.getProductionReadinessBasis();
    result.put("valveInstrumentQualification", basis == null || basis.getValveInstrumentQualification() == null ? null
        : basis.getValveInstrumentQualification().toMap());
    return result;
  }

  private static Map<String, Object> ioList(EngineeringProject project) {
    Map<String, Object> result = header(project, "neqsim_io_list.v1");
    List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
    for (EngineeringRequirement requirement : project.getRequirements()) {
      if (isInstrument(requirement)) {
        Map<String, Object> row = requirement(requirement);
        row.put("ioType",
            requirement.getType() == EngineeringRequirement.Type.CONTROL ? "AO_OR_DO_REVIEW" : "AI_OR_DI_REVIEW");
        row.put("cabinetChannel", "UNASSIGNED");
        rows.add(row);
      }
    }
    result.put("rows", rows);
    result.put("rowCount", Integer.valueOf(rows.size()));
    return result;
  }

  private static Map<String, Object> alarmTripSchedule(EngineeringProject project) {
    Map<String, Object> result = header(project, "neqsim_alarm_trip_schedule.v1");
    List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
    for (EngineeringRequirement requirement : project.getRequirements()) {
      if (requirement.getType() == EngineeringRequirement.Type.ALARM
          || requirement.getType() == EngineeringRequirement.Type.TRIP) {
        Map<String, Object> row = requirement(requirement);
        row.put("setPoint", "CALCULATION_OR_HAZOP_INPUT_REQUIRED");
        row.put("deadband", "PROJECT_INPUT_REQUIRED");
        rows.add(row);
      }
    }
    result.put("rows", rows);
    result.put("rowCount", Integer.valueOf(rows.size()));
    return result;
  }

  private static Map<String, Object> shutdownNarratives(EngineeringProject project) {
    Map<String, Object> result = header(project, "neqsim_shutdown_narratives.v1");
    List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
    for (ShutdownSequence sequence : project.getShutdownSequences()) {
      rows.add(sequence.toMap());
    }
    result.put("sequences", rows);
    result.put("rowCount", Integer.valueOf(rows.size()));
    return result;
  }

  private static Map<String, Object> psvDatasheets(EngineeringProject project) {
    Map<String, Object> result = header(project, "neqsim_psv_datasheets.v1");
    List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
    for (ReliefDeviceDesignInput input : project.getReliefDeviceDesignInputs()) {
      Map<String, Object> row = input.toMap();
      row.put("standards", "API 520/API 521 project-applicable editions");
      row.put("approvalStatus", input.getMissingFields().isEmpty() ? "REVIEW_REQUIRED" : "INCOMPLETE");
      rows.add(row);
    }
    if (project.getLatestEngineeringDesignLoopResult() != null) {
      for (EngineeringDesignValue value : project.getLatestEngineeringDesignLoopResult().getState().getValues()
          .values()) {
        if (value.getKey().endsWith(".proposedPsvSetPressure")) {
          Map<String, Object> row = value.toMap();
          row.put("deviceTag", "UNASSIGNED");
          row.put("protectedEquipmentTag",
              value.getKey().substring(0, value.getKey().length() - ".proposedPsvSetPressure".length()));
          row.put("standards", "API 520/API 521 project-applicable editions");
          row.put("approvalStatus", "INCOMPLETE_REVIEW_REQUIRED");
          rows.add(row);
        }
      }
    }
    result.put("datasheets", rows);
    result.put("rowCount", Integer.valueOf(rows.size()));
    return result;
  }

  private static Map<String, Object> flareBlowdownReport(EngineeringProject project) {
    Map<String, Object> result = header(project, "neqsim_flare_blowdown_report.v1");
    result.put("coupledStudyCount", Integer.valueOf(project.getCoupledReliefBlowdownFlareStudies().size()));
    result.put("dynamicStudyCount", Integer.valueOf(project.getBlowdownFlareStudies().size()));
    result.put("dynamicScenarioCount", Integer.valueOf(project.getDynamicSafetyScenarios().size()));
    result.put("requiredInterfaces", java.util.Arrays.asList("flare network hydraulics", "radiation", "dispersion",
        "noise", "minimum metal temperature"));
    EngineeringProductionReadinessBasis basis = project.getProductionReadinessBasis();
    result.put("flareConsequenceQualification", basis == null || basis.getFlareConsequenceQualification() == null ? null
        : basis.getFlareConsequenceQualification().toMap());
    result.put("transientPipingQualification", basis == null || basis.getTransientPipingQualification() == null ? null
        : basis.getTransientPipingQualification().toMap());
    result.put("approvalStatus", "REVIEW_REQUIRED");
    return result;
  }

  private static Map<String, Object> utilitySummary(EngineeringProject project, EngineeringDesignEnvelope envelope) {
    Map<String, Object> result = header(project, "neqsim_utility_summary.v1");
    List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
    if (envelope != null) {
      for (EngineeringDesignEnvelope.GoverningValue value : envelope.getGoverningValues().values()) {
        String metric = value.getMetric().getId().toLowerCase();
        if (metric.contains("duty") || metric.contains("power") || metric.contains("utility")) {
          rows.add(value.toMap());
        }
      }
    }
    result.put("governingLoads", rows);
    result.put("simultaneousDemandRule", "PROJECT_INPUT_REQUIRED");
    result.put("approvalStatus", "REVIEW_REQUIRED");
    return result;
  }

  private static Map<String, Object> materialsReport(EngineeringProject project) {
    Map<String, Object> result = header(project, "neqsim_materials_selection_report.v1");
    List<Map<String, Object>> values = new ArrayList<Map<String, Object>>();
    if (project.getLatestEngineeringDesignLoopResult() != null) {
      for (EngineeringDesignValue value : project.getLatestEngineeringDesignLoopResult().getState().getValues()
          .values()) {
        if (value.getKey().contains("corrosionAllowance") || value.getKey().contains("designLife")
            || value.getKey().contains("minimumDesignMetalTemperature")) {
          values.add(value.toMap());
        }
      }
    }
    result.put("calculatedValues", values);
    EngineeringProductionReadinessBasis basis = project.getProductionReadinessBasis();
    result.put("mechanicalIntegrityQualification",
        basis == null || basis.getMechanicalIntegrityQualification() == null ? null
            : basis.getMechanicalIntegrityQualification().toMap());
    result.put("standard", "NORSOK M-001:2025");
    result.put("finalMetallurgyApproved", Boolean.FALSE);
    return result;
  }

  private static Map<String, Object> externalEvidenceRegister(EngineeringProject project) {
    Map<String, Object> result = header(project, EngineeringSchemaCatalog.EXTERNAL_EVIDENCE_REGISTER);
    result.put("schemaUri", EngineeringSchemaCatalog.schemaUri(EngineeringSchemaCatalog.EXTERNAL_EVIDENCE_REGISTER));
    EngineeringProductionReadinessBasis basis = project.getProductionReadinessBasis();
    EngineeringExternalEvidenceRegister register = basis == null ? null : basis.getExternalEvidenceRegister();
    result.put("register", register == null ? null : register.toMap());
    result.put("assessment", EngineeringExternalEvidenceAssessment.assess(register).toMap());
    EngineeringExternalEvidenceDocumentIntegrity integrity = basis == null ? null
        : basis.getExternalEvidenceDocumentIntegrity();
    result.put("documentIntegrity", EngineeringExternalEvidenceDocumentIntegrity.assess(register, integrity).toMap());
    result.put("evidenceGeneratedBySimulator", Boolean.FALSE);
    result.put("approvalGrantedBySimulator", Boolean.FALSE);
    result.put("governance",
        "Records are controlled receipts from accountable external parties; NeqSim verifies completeness and content integrity only");
    return result;
  }

  private static Map<String, Object> unresolvedActions(EngineeringProject project) {
    Map<String, Object> result = header(project, "neqsim_unresolved_engineering_actions.v1");
    List<Map<String, Object>> actions = new ArrayList<Map<String, Object>>();
    for (EngineeringBoundary boundary : project.getBoundaries()) {
      if (!boundary.isResolved()) {
        actions
            .add(action("BOUNDARY", boundary.getId(), "Resolve " + boundary.getType().name() + " boundary", "PIPING"));
      }
    }
    for (EngineeringRequirement requirement : project.getRequirements()) {
      if (requirement.getApprovalStatus() != neqsim.process.engineering.EngineeringApprovalStatus.APPROVED) {
        actions.add(action("REQUIREMENT", requirement.getId(), "Review and approve " + requirement.getTitle(),
            requirement.getType().name()));
      }
    }
    for (ReliefDeviceDesignInput input : project.getReliefDeviceDesignInputs()) {
      for (String missing : input.getMissingFields()) {
        actions.add(action("RELIEF_DEVICE", input.getDeviceTag(), "Supply " + missing, "PROCESS_SAFETY"));
      }
    }
    if (project.getLatestEngineeringDesignLoopResult() != null) {
      if (!project.getLatestEngineeringDesignLoopResult().isConverged()) {
        actions.add(
            action("DESIGN_LOOP", project.getProjectId(), "Resolve non-converged engineering design loop", "PROCESS"));
      }
      EngineeringDesignIteration last = project.getLatestEngineeringDesignLoopResult().getIterations()
          .get(project.getLatestEngineeringDesignLoopResult().getIterations().size() - 1);
      Object constraints = last.toMap().get("constraints");
      if (constraints instanceof List<?>) {
        for (Object item : (List<?>) constraints) {
          if (item instanceof Map<?, ?> && Boolean.FALSE.equals(((Map<?, ?>) item).get("satisfied"))) {
            actions.add(action("CONSTRAINT", String.valueOf(((Map<?, ?>) item).get("id")),
                "Resolve failed engineering constraint", "ENGINEERING"));
          }
        }
      }
    }
    EngineeringProductionReadinessAssessment.Result readiness = EngineeringProductionReadinessAssessment.assess(project,
        project.getProductionReadinessBasis());
    for (String failedGate : readiness.getFailedGates()) {
      actions.add(action("PRODUCTION_READINESS", failedGate, "Close production-readiness gate " + failedGate,
          "ENGINEERING_ASSURANCE"));
    }
    result.put("actions", actions);
    result.put("openActionCount", Integer.valueOf(actions.size()));
    result.put("fitnessForConstruction", Boolean.FALSE);
    return result;
  }

  private static Map<String, Object> revisionImpact(EngineeringProject project, EngineeringGraphDiff diff) {
    Map<String, Object> result = header(project, "neqsim_revision_impact_report.v1");
    result.put("baselineSupplied", Boolean.valueOf(diff != null));
    result.put("graphDifference", diff == null ? "NO_BASELINE" : diff.toMap());
    result.put("disciplineReviewRequired", Boolean.valueOf(diff != null));
    return result;
  }

  private static boolean isInstrument(EngineeringRequirement requirement) {
    return requirement.getType() == EngineeringRequirement.Type.CONTROL
        || requirement.getType() == EngineeringRequirement.Type.INSTRUMENT
        || requirement.getType() == EngineeringRequirement.Type.ALARM
        || requirement.getType() == EngineeringRequirement.Type.TRIP
        || requirement.getType() == EngineeringRequirement.Type.FIRE_AND_GAS;
  }

  private static Map<String, Object> requirement(EngineeringRequirement requirement) {
    Map<String, Object> row = new LinkedHashMap<String, Object>();
    row.put("tag", requirement.getId());
    row.put("equipmentTag", requirement.getEquipmentTag());
    row.put("type", requirement.getType().name());
    row.put("title", requirement.getTitle());
    row.put("standards", requirement.getStandardReferences());
    row.put("silTarget", requirement.getSilTarget());
    row.put("approvalStatus", requirement.getApprovalStatus().name());
    row.put("evidenceReference", requirement.getReviewRecord());
    return row;
  }

  private static Map<String, Object> action(String type, String subject, String description, String discipline) {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("type", type);
    result.put("subject", subject);
    result.put("description", description);
    result.put("discipline", discipline);
    result.put("status", "OPEN");
    return result;
  }

  private static Map<String, Object> header(EngineeringProject project, String schemaVersion) {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("schemaVersion", schemaVersion);
    result.put("projectId", project.getProjectId());
    result.put("revision", project.getRevision());
    result.put("engineeringApprovalRequired", Boolean.TRUE);
    return result;
  }
}
