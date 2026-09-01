package neqsim.process.engineering.production;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.engineering.EngineeringApprovalStatus;
import neqsim.process.engineering.EngineeringEvidenceRecord;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.EngineeringRequirement;
import neqsim.process.engineering.ReliefScenarioBasis;
import neqsim.process.engineering.SafetyFunctionDesign;
import neqsim.process.engineering.ShutdownSequence;

/** Accountable HAZOP/LOPA/SRS and shutdown completeness gate; it never creates risk decisions. */
public final class EngineeringSafetyLifecycleAssessment {
  private EngineeringSafetyLifecycleAssessment() {
  }

  public static Result assess(EngineeringProject project) {
    return assess(project, null);
  }

  /** Assesses safety completeness using legacy project records and the controlled external evidence register. */
  public static Result assess(EngineeringProject project, EngineeringExternalEvidenceRegister externalRegister) {
    if (project == null) {
      throw new IllegalArgumentException("project must not be null");
    }
    List<Map<String, Object>> findings = new ArrayList<Map<String, Object>>();
    EngineeringExternalEvidenceAssessment.Result external = externalRegister == null ? null
        : EngineeringExternalEvidenceAssessment.assess(externalRegister);
    boolean approvedHazopEvidence = external != null
        && external.isTypePassed(EngineeringExternalEvidenceRecord.Type.HAZOP_DECISION);
    for (EngineeringEvidenceRecord evidence : project.getEvidenceRecords()) {
      if ("HAZOP".equalsIgnoreCase(evidence.getDocumentType())
          && evidence.getApprovalStatus() == EngineeringApprovalStatus.APPROVED
          && evidence.getMissingFields().isEmpty()) {
        approvedHazopEvidence = true;
      }
    }
    if (!approvedHazopEvidence) {
      findings.add(finding("HAZOP_EVIDENCE", "Approved and complete HAZOP evidence is required"));
    }
    if (external != null && !external.isTypePassed(EngineeringExternalEvidenceRecord.Type.LOPA_DECISION)) {
      findings.add(finding("LOPA_EVIDENCE", "Approved and complete LOPA evidence is required"));
    }
    if (external != null && !external.isTypePassed(EngineeringExternalEvidenceRecord.Type.SRS_APPROVAL)) {
      findings.add(finding("SRS_EVIDENCE", "Approved and complete SRS evidence is required"));
    }
    for (ReliefScenarioBasis basis : project.getReliefScenarioBases()) {
      for (String missing : basis.getMissingFields()) {
        findings.add(finding("RELIEF_SCENARIO_" + basis.getEquipmentTag(), "Missing " + missing));
      }
    }
    boolean tripOrFgRequired = false;
    for (EngineeringRequirement requirement : project.getRequirements()) {
      if (requirement.getType() == EngineeringRequirement.Type.TRIP
          || requirement.getType() == EngineeringRequirement.Type.FIRE_AND_GAS) {
        tripOrFgRequired = true;
        if (requirement.getApprovalStatus() != EngineeringApprovalStatus.APPROVED) {
          findings.add(finding("REQUIREMENT_" + requirement.getId(), "Safety requirement is not approved"));
        }
      }
    }
    if (tripOrFgRequired && project.getSafetyFunctionDesigns().isEmpty()) {
      findings.add(finding("SAFETY_FUNCTION_DESIGN", "Approved SIF design is required for trip/F&G requirements"));
    }
    for (SafetyFunctionDesign design : project.getSafetyFunctionDesigns()) {
      for (String missing : design.getMissingFields()) {
        findings.add(finding("SIF_" + design.getSifTag(), "Missing " + missing));
      }
      if (design.getApprovalStatus() != EngineeringApprovalStatus.APPROVED
          || design.getAchievedSil() < design.getTargetSil() || !design.areArchitecturalConstraintsMet()) {
        findings.add(finding("SIF_" + design.getSifTag(), "SIF approval, target SIL or architecture is incomplete"));
      }
    }
    if (tripOrFgRequired && project.getShutdownSequences().isEmpty()) {
      findings.add(finding("SHUTDOWN_SEQUENCE", "Approved shutdown sequence is required"));
    }
    for (ShutdownSequence sequence : project.getShutdownSequences()) {
      for (String missing : sequence.getMissingFields()) {
        findings.add(finding("SHUTDOWN_" + sequence.getSequenceId(), "Missing " + missing));
      }
      if (sequence.getApprovalStatus() != EngineeringApprovalStatus.APPROVED
          || !sequence.isWithinResponseTimeBudget()) {
        findings.add(finding("SHUTDOWN_" + sequence.getSequenceId(),
            "Shutdown sequence approval or response-time verification is incomplete"));
      }
    }
    return new Result(findings);
  }

  private static Map<String, Object> finding(String code, String message) {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("code", code);
    result.put("message", message);
    result.put("severity", "BLOCKER");
    return result;
  }

  /** Immutable safety lifecycle gate result. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final List<Map<String, Object>> findings;

    Result(List<Map<String, Object>> findings) {
      this.findings = new ArrayList<Map<String, Object>>(findings);
    }

    public boolean isPassed() {
      return findings.isEmpty();
    }

    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("passed", Boolean.valueOf(isPassed()));
      result.put("findings", new ArrayList<Map<String, Object>>(findings));
      result.put("hazopLopaSrsDecisionsGeneratedBySimulator", Boolean.FALSE);
      result.put("engineeringApprovalRequired", Boolean.TRUE);
      return result;
    }
  }
}
