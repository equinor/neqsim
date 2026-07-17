package neqsim.process.engineering.deliverables;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import neqsim.process.engineering.EngineeringApprovalRecord;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.model.EngineeringGraph;
import neqsim.process.engineering.model.EngineeringGraphDiff;
import neqsim.process.engineering.model.EngineeringNode;
import neqsim.process.engineering.validation.EngineeringSchemaCatalog;

/** Builds an approval history and effective-state ledger with revision-impact invalidation. */
public final class EngineeringApprovalLedger {
  private EngineeringApprovalLedger() {
  }

  public static Map<String, Object> build(EngineeringProject project, EngineeringGraph graph,
      EngineeringGraphDiff revisionDiff) {
    if (project == null || graph == null) {
      throw new IllegalArgumentException("project and graph are required");
    }
    Map<String, EngineeringApprovalRecord> recordsById = validateRecords(project, graph);
    Map<String, EngineeringApprovalRecord> effectiveRecords = new LinkedHashMap<String, EngineeringApprovalRecord>();
    for (EngineeringApprovalRecord record : project.getApprovalRecords()) {
      String key = decisionKey(record.getSubjectNodeId(), record.getDiscipline());
      effectiveRecords.put(key, record);
    }
    Set<String> impacted = revisionDiff == null ? new LinkedHashSet<String>() : revisionDiff.getImpactedNodeIds();
    List<Map<String, Object>> effectiveStates = new ArrayList<Map<String, Object>>();
    int approved = 0;
    int rejected = 0;
    int revalidation = 0;
    int reviewRequired = 0;
    for (EngineeringNode node : graph.getNodes().values()) {
      List<EngineeringApprovalRecord> decisions = decisionsFor(node.getId(), effectiveRecords);
      if (decisions.isEmpty()) {
        continue;
      }
      for (EngineeringApprovalRecord decision : decisions) {
        String status = impacted.contains(node.getId()) ? "REVALIDATION_REQUIRED" : decision.getStatus().name();
        Map<String, Object> state = new LinkedHashMap<String, Object>();
        state.put("subjectNodeId", node.getId());
        state.put("discipline", decision.getDiscipline());
        state.put("effectiveRecordId", decision.getId());
        state.put("status", status);
        state.put("revisionImpacted", Boolean.valueOf(impacted.contains(node.getId())));
        effectiveStates.add(state);
        if ("APPROVED".equals(status)) {
          approved++;
        } else if ("REJECTED".equals(status)) {
          rejected++;
        } else if ("REVALIDATION_REQUIRED".equals(status)) {
          revalidation++;
        } else {
          reviewRequired++;
        }
      }
    }
    List<Map<String, Object>> records = new ArrayList<Map<String, Object>>();
    for (EngineeringApprovalRecord record : project.getApprovalRecords()) {
      records.add(record.toMap());
    }
    Map<String, Object> document = new LinkedHashMap<String, Object>();
    document.put("schemaVersion", EngineeringSchemaCatalog.APPROVAL_LEDGER);
    document.put("schemaUri", EngineeringSchemaCatalog.schemaUri(EngineeringSchemaCatalog.APPROVAL_LEDGER));
    document.put("projectId", project.getProjectId());
    document.put("revision", project.getRevision());
    document.put("graphFingerprint", graph.toMap().get("fingerprint"));
    document.put("baselineRevision", revisionDiff == null ? "NOT_PROVIDED" : revisionDiff.toMap().get("fromRevision"));
    document.put("records", records);
    document.put("effectiveStates", effectiveStates);
    document.put("revisionImpactedNodeIds", new ArrayList<String>(impacted));
    Map<String, Object> summary = new LinkedHashMap<String, Object>();
    summary.put("recordCount", Integer.valueOf(recordsById.size()));
    summary.put("approvedCount", Integer.valueOf(approved));
    summary.put("rejectedCount", Integer.valueOf(rejected));
    summary.put("reviewRequiredCount", Integer.valueOf(reviewRequired));
    summary.put("revalidationRequiredCount", Integer.valueOf(revalidation));
    document.put("summary", summary);
    return document;
  }

  private static Map<String, EngineeringApprovalRecord> validateRecords(EngineeringProject project,
      EngineeringGraph graph) {
    Map<String, EngineeringApprovalRecord> result = new LinkedHashMap<String, EngineeringApprovalRecord>();
    for (EngineeringApprovalRecord record : project.getApprovalRecords()) {
      if (graph.getNode(record.getSubjectNodeId()) == null) {
        throw new IllegalArgumentException(
            "Approval record " + record.getId() + " references unknown graph node " + record.getSubjectNodeId());
      }
      if (result.put(record.getId(), record) != null) {
        throw new IllegalArgumentException("Duplicate approval record " + record.getId());
      }
      if (!record.getSupersedesRecordId().isEmpty() && !result.containsKey(record.getSupersedesRecordId())) {
        throw new IllegalArgumentException("Approval record " + record.getId() + " supersedes unknown or later record "
            + record.getSupersedesRecordId());
      }
    }
    return result;
  }

  private static List<EngineeringApprovalRecord> decisionsFor(String nodeId,
      Map<String, EngineeringApprovalRecord> effectiveRecords) {
    List<EngineeringApprovalRecord> result = new ArrayList<EngineeringApprovalRecord>();
    for (EngineeringApprovalRecord record : effectiveRecords.values()) {
      if (nodeId.equals(record.getSubjectNodeId())) {
        result.add(record);
      }
    }
    return result;
  }

  private static String decisionKey(String subjectNodeId, String discipline) {
    return subjectNodeId + "|" + discipline;
  }
}
