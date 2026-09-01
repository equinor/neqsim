package neqsim.process.engineering.production;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/** Explicit project requirement for one class and scope of external engineering evidence. */
public final class EngineeringExternalEvidenceRequirement implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final String id;
  private final EngineeringExternalEvidenceRecord.Type type;
  private final String scopeReference;
  private final String description;
  private final String requiredDecisionRole;

  public EngineeringExternalEvidenceRequirement(String id, EngineeringExternalEvidenceRecord.Type type,
      String scopeReference, String description) {
    this(id, type, scopeReference, description, "");
  }

  public EngineeringExternalEvidenceRequirement(String id, EngineeringExternalEvidenceRecord.Type type,
      String scopeReference, String description, String requiredDecisionRole) {
    this.id = requireText(id, "id");
    if (type == null) {
      throw new IllegalArgumentException("type must not be null");
    }
    this.type = type;
    this.scopeReference = requireText(scopeReference, "scopeReference");
    this.description = requireText(description, "description");
    this.requiredDecisionRole = text(requiredDecisionRole);
  }

  public String getId() {
    return id;
  }

  public EngineeringExternalEvidenceRecord.Type getType() {
    return type;
  }

  public String getScopeReference() {
    return scopeReference;
  }

  public String getDescription() {
    return description;
  }

  public String getRequiredDecisionRole() {
    return requiredDecisionRole;
  }

  public boolean isSatisfiedBy(EngineeringExternalEvidenceRecord record) {
    if (record == null || record.getType() != type || !record.isAcceptedAndComplete()
        || !record.getScopeReferences().contains(scopeReference)) {
      return false;
    }
    return requiredDecisionRole.isEmpty() || requiredDecisionRole.equalsIgnoreCase(record.getDecisionRole());
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("id", id);
    result.put("type", type.name());
    result.put("scopeReference", scopeReference);
    result.put("description", description);
    result.put("requiredDecisionRole", requiredDecisionRole);
    return result;
  }

  private static String requireText(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }

  private static String text(String value) {
    return value == null ? "" : value.trim();
  }
}
