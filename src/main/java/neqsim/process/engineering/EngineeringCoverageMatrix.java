package neqsim.process.engineering;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.pipeline.PipeLineInterface;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.DesignConditions;
import neqsim.process.safety.overpressure.OverpressureProtectionStudy;
import neqsim.process.safety.overpressure.ReliefCause;
import neqsim.process.safety.overpressure.ReliefScenario;

/** Builds per-equipment and per-requirement engineering evidence coverage. */
public final class EngineeringCoverageMatrix {
  private EngineeringCoverageMatrix() {
  }

  /**
   * Evaluates the engineering project without changing approval state.
   *
   * @param project governed engineering project
   * @return JSON-ready coverage matrix
   */
  public static Map<String, Object> evaluate(EngineeringProject project) {
    if (project == null) {
      throw new IllegalArgumentException("project must not be null");
    }
    List<Map<String, Object>> equipmentRows = equipmentRows(project);
    List<Map<String, Object>> requirementRows = requirementRows(project);
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("equipmentCoverage", equipmentRows);
    result.put("requirementCoverage", requirementRows);
    result.put("summary", summary(equipmentRows, requirementRows));
    result.put("governanceNote", "Coverage records evidence and calculation readiness; it does not grant approval.");
    return result;
  }

  private static List<Map<String, Object>> equipmentRows(EngineeringProject project) {
    List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
    for (ProcessEquipmentInterface unit : project.getProcessSystem().getUnitOperations()) {
      if (unit == null || unit instanceof Stream) {
        continue;
      }
      String tag = unit.getName();
      List<EngineeringRequirement> requirements = project.getRequirementsForEquipment(tag);
      int applicable = 1;
      int covered = hasDesignConditions(unit) ? 1 : 0;
      List<String> missing = new ArrayList<String>();
      if (!hasDesignConditions(unit)) {
        missing.add("designConditions");
      }
      if (unit instanceof PipeLineInterface) {
        applicable++;
        if (hasCompleteLineInput(project, tag)) {
          covered++;
        } else {
          missing.add("controlledLineListRow");
        }
      }
      if (hasReliefRequirement(requirements)) {
        applicable++;
        if (hasCompleteReliefCoverage(project, tag)) {
          covered++;
        } else {
          missing.add("credibleReliefScenarioCoverage");
        }
      }
      int safetyRequirements = countSafetyRequirements(requirements);
      applicable += safetyRequirements * 2;
      for (EngineeringRequirement requirement : requirements) {
        if (!isSafetyRequirement(requirement)) {
          continue;
        }
        if (hasCompleteSif(project, requirement.getId())) {
          covered++;
        } else {
          missing.add(requirement.getId() + ":sifVerification");
        }
        if (hasCompleteShutdown(project, requirement.getId())) {
          covered++;
        } else {
          missing.add(requirement.getId() + ":shutdownSequence");
        }
      }
      applicable++;
      if (hasEvidenceForEquipment(project, tag)) {
        covered++;
      } else {
        missing.add("revisionControlledEvidence");
      }
      Map<String, Object> row = new LinkedHashMap<String, Object>();
      row.put("equipmentTag", tag);
      row.put("equipmentClass", unit.getClass().getSimpleName());
      row.put("requirementCount", requirements.size());
      row.put("coveredItemCount", covered);
      row.put("applicableItemCount", applicable);
      row.put("completenessPercent", percentage(covered, applicable));
      row.put("missingItems", missing);
      row.put("approvalState", allRequirementsApproved(requirements) ? "APPROVED" : "REVIEW_REQUIRED");
      rows.add(row);
    }
    return rows;
  }

  private static List<Map<String, Object>> requirementRows(EngineeringProject project) {
    List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
    for (EngineeringRequirement requirement : project.getRequirements()) {
      List<String> sifTags = new ArrayList<String>();
      for (SafetyFunctionDesign design : project.getSafetyFunctionDesigns()) {
        if (requirement.getId().equals(design.getRequirementId())) {
          sifTags.add(design.getSifTag());
        }
      }
      List<String> sequenceIds = new ArrayList<String>();
      for (ShutdownSequence sequence : project.getShutdownSequences()) {
        if (sequence.getRequirementIds().contains(requirement.getId())) {
          sequenceIds.add(sequence.getSequenceId());
        }
      }
      Map<String, Object> row = new LinkedHashMap<String, Object>();
      row.put("requirementId", requirement.getId());
      row.put("equipmentTag", requirement.getEquipmentTag());
      row.put("type", requirement.getType().name());
      row.put("silTarget", requirement.getSilTarget());
      row.put("safetyFunctionTags", sifTags);
      row.put("shutdownSequenceIds", sequenceIds);
      row.put("evidenceRecordIds", evidenceForRequirement(project, requirement.getId()));
      row.put("approvalState", requirement.getApprovalStatus().name());
      row.put("complete", !isSafetyRequirement(requirement)
          || (hasCompleteSif(project, requirement.getId()) && hasCompleteShutdown(project, requirement.getId())));
      rows.add(row);
    }
    return rows;
  }

  private static Map<String, Object> summary(List<Map<String, Object>> equipmentRows,
      List<Map<String, Object>> requirementRows) {
    int equipmentComplete = countBooleanOrPercent(equipmentRows, "completenessPercent");
    int requirementComplete = countBoolean(requirementRows, "complete");
    Map<String, Object> summary = new LinkedHashMap<String, Object>();
    summary.put("equipmentCount", equipmentRows.size());
    summary.put("completeEquipmentCount", equipmentComplete);
    summary.put("equipmentCompletenessPercent", percentage(equipmentComplete, equipmentRows.size()));
    summary.put("requirementCount", requirementRows.size());
    summary.put("completeRequirementCount", requirementComplete);
    summary.put("requirementCompletenessPercent", percentage(requirementComplete, requirementRows.size()));
    summary.put("fitnessForConstruction", false);
    return summary;
  }

  private static int countBooleanOrPercent(List<Map<String, Object>> rows, String key) {
    int count = 0;
    for (Map<String, Object> row : rows) {
      Object value = row.get(key);
      if (value instanceof Number && ((Number) value).doubleValue() >= 100.0) {
        count++;
      }
    }
    return count;
  }

  private static int countBoolean(List<Map<String, Object>> rows, String key) {
    int count = 0;
    for (Map<String, Object> row : rows) {
      if (Boolean.TRUE.equals(row.get(key))) {
        count++;
      }
    }
    return count;
  }

  private static boolean hasDesignConditions(ProcessEquipmentInterface unit) {
    DesignConditions design = unit.getDesignConditions();
    return design != null && !design.isEmpty();
  }

  private static boolean hasCompleteLineInput(EngineeringProject project, String tag) {
    for (LineDesignInput input : project.getLineDesignInputs()) {
      if (tag.equals(input.getEquipmentTag()) && input.getMissingFields().isEmpty()) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasReliefRequirement(List<EngineeringRequirement> requirements) {
    for (EngineeringRequirement requirement : requirements) {
      if (requirement.getType() == EngineeringRequirement.Type.RELIEF) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasCompleteReliefCoverage(EngineeringProject project, String equipmentTag) {
    Set<ReliefCause> evaluated = new HashSet<ReliefCause>();
    for (OverpressureProtectionStudy study : project.getOverpressureStudies()) {
      if (equipmentTag.equals(study.getItem().getName())) {
        for (ReliefScenario scenario : study.getScenarios()) {
          evaluated.add(scenario.getCause());
        }
      }
    }
    if (hasAutomaticBlockedOutletBasis(project, equipmentTag)) {
      evaluated.add(ReliefCause.BLOCKED_OUTLET);
    }
    for (ReliefScenarioBasis basis : project.getReliefScenarioBases()) {
      if (equipmentTag.equals(basis.getEquipmentTag()) && basis.getMissingFields().isEmpty()
          && evaluated.containsAll(basis.getRequiredCauses())) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasAutomaticBlockedOutletBasis(EngineeringProject project, String equipmentTag) {
    ProcessEquipmentInterface unit = project.getProcessSystem().getUnit(equipmentTag);
    if (unit == null) {
      return false;
    }
    DesignConditions design = unit.getDesignConditions();
    if (design == null || !design.isDesignPressureSet() || !design.isReliefSetPressureSet()) {
      return false;
    }
    for (StreamInterface inlet : unit.getInletStreams()) {
      if (inlet == null) {
        continue;
      }
      try {
        if (inlet.getFluid() != null && inlet.getFlowRate("kg/sec") > 0.0) {
          return true;
        }
      } catch (Exception ex) {
        // Continue looking for another initialized inlet.
      }
    }
    return false;
  }

  private static int countSafetyRequirements(List<EngineeringRequirement> requirements) {
    int count = 0;
    for (EngineeringRequirement requirement : requirements) {
      if (isSafetyRequirement(requirement)) {
        count++;
      }
    }
    return count;
  }

  private static boolean isSafetyRequirement(EngineeringRequirement requirement) {
    return requirement.getType() == EngineeringRequirement.Type.TRIP
        || requirement.getType() == EngineeringRequirement.Type.FIRE_AND_GAS;
  }

  private static boolean hasCompleteSif(EngineeringProject project, String requirementId) {
    for (SafetyFunctionDesign design : project.getSafetyFunctionDesigns()) {
      if (requirementId.equals(design.getRequirementId()) && design.getMissingFields().isEmpty()
          && design.getAchievedSil() >= design.getTargetSil() && design.areArchitecturalConstraintsMet()) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasCompleteShutdown(EngineeringProject project, String requirementId) {
    for (ShutdownSequence sequence : project.getShutdownSequences()) {
      if (sequence.getRequirementIds().contains(requirementId) && sequence.getMissingFields().isEmpty()
          && sequence.isWithinResponseTimeBudget()) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasEvidenceForEquipment(EngineeringProject project, String equipmentTag) {
    for (EngineeringEvidenceRecord evidence : project.getEvidenceRecords()) {
      if (evidence.getEquipmentTags().contains(equipmentTag) && evidence.getMissingFields().isEmpty()) {
        return true;
      }
    }
    return false;
  }

  private static List<String> evidenceForRequirement(EngineeringProject project, String requirementId) {
    List<String> result = new ArrayList<String>();
    for (EngineeringEvidenceRecord evidence : project.getEvidenceRecords()) {
      if (evidence.getRequirementIds().contains(requirementId)) {
        result.add(evidence.getDocumentId() + "@" + evidence.getRevision());
      }
    }
    return result;
  }

  private static boolean allRequirementsApproved(List<EngineeringRequirement> requirements) {
    if (requirements.isEmpty()) {
      return false;
    }
    for (EngineeringRequirement requirement : requirements) {
      if (requirement.getApprovalStatus() != EngineeringApprovalStatus.APPROVED) {
        return false;
      }
    }
    return true;
  }

  private static double percentage(int completed, int total) {
    return total <= 0 ? 0.0 : 100.0 * Math.max(0, Math.min(completed, total)) / total;
  }
}
