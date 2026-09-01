package neqsim.process.engineering.production;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Deterministically verifies external evidence coverage without creating or approving evidence. */
public final class EngineeringExternalEvidenceAssessment {
  private EngineeringExternalEvidenceAssessment() {
  }

  public static Result assess(EngineeringExternalEvidenceRegister register) {
    List<EngineeringExternalEvidenceRequirement> requirements = register == null
        ? new ArrayList<EngineeringExternalEvidenceRequirement>()
        : register.getRequirements();
    List<EngineeringExternalEvidenceRecord> records = register == null
        ? new ArrayList<EngineeringExternalEvidenceRecord>()
        : register.getRecords();
    List<Map<String, Object>> findings = new ArrayList<Map<String, Object>>();
    Map<String, EngineeringExternalEvidenceRecord> recordsById = new LinkedHashMap<String, EngineeringExternalEvidenceRecord>();
    Set<String> supersededIds = new LinkedHashSet<String>();
    for (EngineeringExternalEvidenceRecord record : records) {
      recordsById.put(record.getId(), record);
      if (!record.getSupersedesRecordId().isEmpty()) {
        supersededIds.add(record.getSupersedesRecordId());
      }
      if (record.getStatus() == EngineeringExternalEvidenceRecord.Status.ACCEPTED
          && !record.getMissingFields().isEmpty()) {
        findings.add(finding("INCOMPLETE_ACCEPTED_RECORD", record.getType(), record.getId(),
            "Accepted evidence is incomplete: " + record.getMissingFields()));
      }
    }
    validateSupersession(records, recordsById, findings);

    Map<EngineeringExternalEvidenceRecord.Type, List<EngineeringExternalEvidenceRequirement>> requirementsByType = new EnumMap<EngineeringExternalEvidenceRecord.Type, List<EngineeringExternalEvidenceRequirement>>(
        EngineeringExternalEvidenceRecord.Type.class);
    for (EngineeringExternalEvidenceRecord.Type type : EngineeringExternalEvidenceRecord.Type.values()) {
      requirementsByType.put(type, new ArrayList<EngineeringExternalEvidenceRequirement>());
    }
    for (EngineeringExternalEvidenceRequirement requirement : requirements) {
      requirementsByType.get(requirement.getType()).add(requirement);
    }

    Map<EngineeringExternalEvidenceRecord.Type, Boolean> coverage = new EnumMap<EngineeringExternalEvidenceRecord.Type, Boolean>(
        EngineeringExternalEvidenceRecord.Type.class);
    Map<String, List<String>> matchedRecords = new LinkedHashMap<String, List<String>>();
    for (EngineeringExternalEvidenceRecord.Type type : EngineeringExternalEvidenceRecord.Type.values()) {
      List<EngineeringExternalEvidenceRequirement> typedRequirements = requirementsByType.get(type);
      boolean typePassed = !typedRequirements.isEmpty();
      if (typedRequirements.isEmpty()) {
        findings.add(finding("MISSING_REQUIREMENT_DEFINITION", type, type.name(),
            "Define the project scope that requires this external evidence class"));
      }
      for (EngineeringExternalEvidenceRequirement requirement : typedRequirements) {
        List<String> accepted = new ArrayList<String>();
        List<String> rejected = new ArrayList<String>();
        for (EngineeringExternalEvidenceRecord record : records) {
          if (supersededIds.contains(record.getId()) || record.getType() != type
              || !record.getScopeReferences().contains(requirement.getScopeReference())) {
            continue;
          }
          if (requirement.isSatisfiedBy(record)) {
            accepted.add(record.getId());
          } else if (record.getStatus() == EngineeringExternalEvidenceRecord.Status.REJECTED) {
            rejected.add(record.getId());
          }
        }
        matchedRecords.put(requirement.getId(), accepted);
        if (accepted.isEmpty()) {
          typePassed = false;
          findings.add(finding("UNSATISFIED_REQUIREMENT", type, requirement.getId(),
              "Attach a complete accepted external decision for scope " + requirement.getScopeReference()));
        }
        if (!accepted.isEmpty() && !rejected.isEmpty()) {
          typePassed = false;
          findings.add(finding("CONFLICTING_ACTIVE_DECISIONS", type, requirement.getId(),
              "Resolve accepted " + accepted + " versus rejected " + rejected + " evidence revisions"));
        }
      }
      coverage.put(type, Boolean.valueOf(typePassed));
    }
    for (Map<String, Object> finding : findings) {
      EngineeringExternalEvidenceRecord.Type type = EngineeringExternalEvidenceRecord.Type
          .valueOf(String.valueOf(finding.get("type")));
      coverage.put(type, Boolean.FALSE);
    }
    return new Result(register, coverage, matchedRecords, findings);
  }

  private static void validateSupersession(List<EngineeringExternalEvidenceRecord> records,
      Map<String, EngineeringExternalEvidenceRecord> recordsById, List<Map<String, Object>> findings) {
    for (EngineeringExternalEvidenceRecord record : records) {
      if (record.getSupersedesRecordId().isEmpty()) {
        continue;
      }
      EngineeringExternalEvidenceRecord superseded = recordsById.get(record.getSupersedesRecordId());
      if (superseded == null) {
        findings.add(finding("UNKNOWN_SUPERSEDED_RECORD", record.getType(), record.getId(),
            "Superseded record does not exist: " + record.getSupersedesRecordId()));
      } else if (superseded.getType() != record.getType()) {
        findings.add(finding("CROSS_TYPE_SUPERSESSION", record.getType(), record.getId(),
            "Evidence may supersede only a record of the same type"));
      }
      Set<String> visited = new HashSet<String>();
      EngineeringExternalEvidenceRecord cursor = record;
      while (cursor != null && !cursor.getSupersedesRecordId().isEmpty()) {
        if (!visited.add(cursor.getId())) {
          findings.add(finding("SUPERSESSION_CYCLE", record.getType(), record.getId(),
              "Evidence supersession chain contains a cycle"));
          break;
        }
        cursor = recordsById.get(cursor.getSupersedesRecordId());
      }
    }
  }

  private static Map<String, Object> finding(String code, EngineeringExternalEvidenceRecord.Type type, String subject,
      String action) {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("code", code);
    result.put("type", type.name());
    result.put("subject", subject);
    result.put("requiredAction", action);
    result.put("severity", "BLOCKER");
    return result;
  }

  /** Immutable external assurance result with per-evidence-class coverage. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final EngineeringExternalEvidenceRegister register;
    private final Map<EngineeringExternalEvidenceRecord.Type, Boolean> coverage;
    private final Map<String, List<String>> matchedRecords;
    private final List<Map<String, Object>> findings;

    Result(EngineeringExternalEvidenceRegister register, Map<EngineeringExternalEvidenceRecord.Type, Boolean> coverage,
        Map<String, List<String>> matchedRecords, List<Map<String, Object>> findings) {
      this.register = register;
      this.coverage = new EnumMap<EngineeringExternalEvidenceRecord.Type, Boolean>(coverage);
      this.matchedRecords = new LinkedHashMap<String, List<String>>();
      for (Map.Entry<String, List<String>> match : matchedRecords.entrySet()) {
        this.matchedRecords.put(match.getKey(), new ArrayList<String>(match.getValue()));
      }
      this.findings = new ArrayList<Map<String, Object>>(findings);
    }

    public boolean isPassed() {
      return findings.isEmpty()
          && coverage.keySet().containsAll(EnumSet.allOf(EngineeringExternalEvidenceRecord.Type.class))
          && !coverage.containsValue(Boolean.FALSE);
    }

    public boolean isTypePassed(EngineeringExternalEvidenceRecord.Type type) {
      return Boolean.TRUE.equals(coverage.get(type));
    }

    public List<Map<String, Object>> getFindings() {
      return new ArrayList<Map<String, Object>>(findings);
    }

    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("passed", Boolean.valueOf(isPassed()));
      Map<String, Object> coverageMap = new LinkedHashMap<String, Object>();
      for (EngineeringExternalEvidenceRecord.Type type : EngineeringExternalEvidenceRecord.Type.values()) {
        coverageMap.put(type.name(), Boolean.valueOf(isTypePassed(type)));
      }
      result.put("coverage", coverageMap);
      result.put("matchedRecords", new LinkedHashMap<String, List<String>>(matchedRecords));
      result.put("findings", getFindings());
      result.put("register", register == null ? null : register.toMap());
      result.put("constructionAuthorityEvidenceAccepted",
          Boolean.valueOf(isTypePassed(EngineeringExternalEvidenceRecord.Type.CONSTRUCTION_AUTHORITY)));
      result.put("evidenceOrApprovalGeneratedBySimulator", Boolean.FALSE);
      result.put("simulatorGrantedConstructionAuthority", Boolean.FALSE);
      return result;
    }
  }
}
