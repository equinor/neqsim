package neqsim.process.engineering.production;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Revision-controlled external evidence requirements and supplied decision receipts. */
public final class EngineeringExternalEvidenceRegister implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final List<EngineeringExternalEvidenceRequirement> requirements = new ArrayList<EngineeringExternalEvidenceRequirement>();
  private final List<EngineeringExternalEvidenceRecord> records = new ArrayList<EngineeringExternalEvidenceRecord>();

  /** Creates the minimum project-level requirement set; projects should add equipment-specific requirements. */
  public static EngineeringExternalEvidenceRegister productionMinimum(String projectScopeReference) {
    String scope = requireText(projectScopeReference, "projectScopeReference");
    EngineeringExternalEvidenceRegister result = new EngineeringExternalEvidenceRegister();
    result.addRequirement(
        requirement("EXT-ACCOUNTABLE-APPROVAL", EngineeringExternalEvidenceRecord.Type.ACCOUNTABLE_ENGINEERING_APPROVAL,
            scope, "Accountable engineering approval for the governed design revision"));
    result.addRequirement(requirement("EXT-VENDOR-GUARANTEE", EngineeringExternalEvidenceRecord.Type.VENDOR_GUARANTEE,
        scope, "Accepted vendor guarantee coverage for supplied equipment and instruments"));
    result.addRequirement(requirement("EXT-HAZOP", EngineeringExternalEvidenceRecord.Type.HAZOP_DECISION, scope,
        "Approved HAZOP decisions and closed actions for the governed scope"));
    result.addRequirement(requirement("EXT-LOPA", EngineeringExternalEvidenceRecord.Type.LOPA_DECISION, scope,
        "Approved LOPA decisions and independent-protection-layer basis"));
    result.addRequirement(requirement("EXT-SRS", EngineeringExternalEvidenceRecord.Type.SRS_APPROVAL, scope,
        "Approved safety requirements specification"));
    result.addRequirement(
        requirement("EXT-INDEPENDENT-VALIDATION", EngineeringExternalEvidenceRecord.Type.INDEPENDENT_VALIDATION, scope,
            "Independent validation acceptance covering methods, inputs and results"));
    result.addRequirement(
        requirement("EXT-CONSTRUCTION-AUTHORITY", EngineeringExternalEvidenceRecord.Type.CONSTRUCTION_AUTHORITY, scope,
            "Construction authority release for the governed design revision"));
    return result;
  }

  public EngineeringExternalEvidenceRegister addRequirement(EngineeringExternalEvidenceRequirement value) {
    if (value == null) {
      throw new IllegalArgumentException("requirement must not be null");
    }
    for (EngineeringExternalEvidenceRequirement existing : requirements) {
      if (existing.getId().equals(value.getId())) {
        throw new IllegalArgumentException("Duplicate external evidence requirement " + value.getId());
      }
    }
    requirements.add(value);
    return this;
  }

  public EngineeringExternalEvidenceRegister addRecord(EngineeringExternalEvidenceRecord value) {
    if (value == null) {
      throw new IllegalArgumentException("record must not be null");
    }
    for (EngineeringExternalEvidenceRecord existing : records) {
      if (existing.getId().equals(value.getId())) {
        throw new IllegalArgumentException("Duplicate external evidence record " + value.getId());
      }
      if (existing.getDocumentId().equals(value.getDocumentId())
          && existing.getRevision().equals(value.getRevision())) {
        throw new IllegalArgumentException(
            "Duplicate external evidence document revision " + value.getDocumentId() + "@" + value.getRevision());
      }
    }
    records.add(value);
    return this;
  }

  public List<EngineeringExternalEvidenceRequirement> getRequirements() {
    return Collections.unmodifiableList(requirements);
  }

  public List<EngineeringExternalEvidenceRecord> getRecords() {
    return Collections.unmodifiableList(records);
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    List<Map<String, Object>> requirementMaps = new ArrayList<Map<String, Object>>();
    for (EngineeringExternalEvidenceRequirement requirement : requirements) {
      requirementMaps.add(requirement.toMap());
    }
    List<Map<String, Object>> recordMaps = new ArrayList<Map<String, Object>>();
    for (EngineeringExternalEvidenceRecord record : records) {
      recordMaps.add(record.toMap());
    }
    result.put("requirements", requirementMaps);
    result.put("records", recordMaps);
    return result;
  }

  private static EngineeringExternalEvidenceRequirement requirement(String id,
      EngineeringExternalEvidenceRecord.Type type, String scope, String description) {
    return new EngineeringExternalEvidenceRequirement(id, type, scope, description);
  }

  private static String requireText(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }
}
